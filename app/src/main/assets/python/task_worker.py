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


def _run_task(params):
    task_id = str(params["taskId"])
    script_content = params["scriptContent"]
    sandbox_dir = params["sandboxDir"]
    user_script_path = params["userScriptPath"]
    restriction_module = params["restrictionModule"]
    args_object = _parse_args(params.get("argsJson"))

    original_cwd = os.getcwd()
    before_modules = set(sys.modules.keys())
    worker_globals = {
        "__name__": "__worker_restrictions__",
        "__file__": os.path.join(sandbox_dir, "sandbox_restrictions.py"),
        "__builtins__": __builtins__,
    }

    output_buffer = io.StringIO()
    error_buffer = io.StringIO()
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

    return result_text, output_buffer.getvalue(), error_buffer.getvalue()


def run_task(params_payload):
    started_at = time.time()
    payload = {
        "success": False,
        "result": "",
        "error": "",
        "stdout": "",
        "stderr": "",
        "executionTimeMs": 0,
    }

    try:
        params = _parse_params(params_payload)
        result_text, stdout_content, stderr_content = _run_task(params)
        payload["success"] = True
        payload["result"] = result_text
        payload["stdout"] = stdout_content
        payload["stderr"] = stderr_content
    except Exception as exc:
        payload["success"] = False
        payload["error"] = str(exc)
        payload["stderr"] = traceback.format_exc()
    finally:
        payload["executionTimeMs"] = int((time.time() - started_at) * 1000)

    return json.dumps(payload)
