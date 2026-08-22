import inspect
import io
import json
import os
import sys
import time
import traceback
from contextlib import redirect_stdout, redirect_stderr


def _parse_params(params_payload):
    if isinstance(params_payload, str):
        return json.loads(params_payload)
    if isinstance(params_payload, dict):
        return params_payload
    raise TypeError("Unsupported worker params payload")


def _parse_args(args_payload):
    if args_payload is None:
        return {}
    if isinstance(args_payload, dict):
        return args_payload
    if isinstance(args_payload, str):
        if not args_payload.strip():
            return {}
        parsed = json.loads(args_payload)
        if not isinstance(parsed, dict):
            raise TypeError("Task args must be a JSON object")
        return parsed
    raise TypeError("Task args must be a JSON object")

def _open_line_buffered(path):
    """Open a text file that flushes after every write for real-time log visibility."""
    f = open(path, "w", encoding="utf-8", buffering=1)  # line-buffered

    # Wrap so that even writes without a trailing newline are flushed immediately.
    class _FlushWrapper:
        def __init__(self, inner):
            self._inner = inner

        def write(self, data):
            self._inner.write(data)
            self._inner.flush()

        def flush(self):
            self._inner.flush()

        def __getattr__(self, name):
            return getattr(self._inner, name)

    return _FlushWrapper(f)


def get_output_paths(output_dir):
    stdout_path = os.path.join(output_dir, f"stdout.log")
    stderr_path = os.path.join(output_dir, f"stderr.log")
    return stdout_path, stderr_path


def _run_task(params, stdfiles=None):
    task_id = str(params["taskId"])
    script_content = params["scriptContent"]
    sandbox_dir = params["sandboxDir"]
    user_script_path = params["userScriptPath"]
    restriction_module = params["restrictionModule"]
    args_object = _parse_args(params.get("argsJson"))
    output_dir = params.get("output_dir", "")

    original_cwd = os.getcwd()
    before_modules = set(sys.modules.keys())
    worker_globals = {
        "__name__": "__worker_restrictions__",
        "__file__": os.path.join(sandbox_dir, "sandbox_restrictions.py"),
        "__builtins__": __builtins__,
    }

    output_buffer = _open_line_buffered(stdfiles["stdout"])
    error_buffer = _open_line_buffered(stdfiles["stderr"])
        
    result_text = ""

    try:
        os.chdir(sandbox_dir)
        with open(user_script_path, "w", encoding="utf-8") as user_script:
            user_script.write(script_content)

        # Apply per-task filesystem restrictions inside this execution scope.
        exec(restriction_module, worker_globals, worker_globals)

        task_globals = {
            "__name__": f"task_user_{task_id.replace('-', '_')}",
            "__file__": user_script_path,
            "__builtins__": __builtins__,
        }

        with redirect_stdout(output_buffer), redirect_stderr(error_buffer):
            compiled_user_code = compile(script_content, user_script_path, "exec")
            exec(compiled_user_code, task_globals, task_globals)

            entrypoint = task_globals.get("main")
            if entrypoint is None:
                raise AttributeError("No callable entrypoint found in script. Expected: main")
            if not callable(entrypoint):
                raise TypeError("Entrypoint main is not callable")

            sig = inspect.signature(entrypoint)
            required_params = [
                p
                for p in sig.parameters.values()
                if p.default is inspect._empty
                and p.kind in (p.POSITIONAL_ONLY, p.POSITIONAL_OR_KEYWORD)
            ]
            positional_params = [
                p
                for p in sig.parameters.values()
                if p.kind in (p.POSITIONAL_ONLY, p.POSITIONAL_OR_KEYWORD)
            ]
            has_varargs = any(p.kind == p.VAR_POSITIONAL for p in sig.parameters.values())

            if len(required_params) > 1 and not has_varargs:
                raise TypeError("Entrypoint main must accept zero or one required positional argument")

            if positional_params or has_varargs:
                entrypoint_result = entrypoint(args_object)
            else:
                entrypoint_result = entrypoint()
            if entrypoint_result is not None:
                result_text = str(entrypoint_result)
    finally:
        restore_fn = worker_globals.get("restore_restrictions")
        if callable(restore_fn):
            restore_fn()

        os.chdir(original_cwd)

        # Drop modules imported during this task to reduce cross-task memory/state leakage.
        after_modules = set(sys.modules.keys())
        for module_name in (after_modules - before_modules):
            sys.modules.pop(module_name, None)

        # Close file handles when using file-based output.
        if stdfiles is not None:
            output_buffer.close()
            error_buffer.close()

    return result_text


def run_task(params_payload):
    started_at = time.time()
    payload = {
        "success": False,
        "result": "",
        "error": "",
        "stdout": "",
        "stderr": "",
        "executionTimeMs": 0,
        "memoryUsage": 0,
    }
    
    params = _parse_params(params_payload)
    
    # Writes the error into the output log
    stderr_path, stdout_path = get_output_paths(params.get("output_dir", ""))
    
    payload["output_dir"] = params.get("output_dir", "")
    payload["stdfiles"] = {
        "stdout": stdout_path,
        "stderr": stderr_path,
    }

    try:        
        result_text = _run_task(params, payload["stdfiles"])
        payload["success"] = True
        payload["result"] = result_text
    except Exception as exc:
        payload["success"] = False
        payload["error"] = str(exc)
                
        with open(stderr_path, "a", encoding="utf-8") as error_buffer:
            error_buffer.write(f"Exception: {str(exc)}\n")
            traceback.print_exc(file=error_buffer)
         
    finally:
        payload["executionTimeMs"] = int((time.time() - started_at) * 1000)

   
    
        
    return json.dumps(payload)
