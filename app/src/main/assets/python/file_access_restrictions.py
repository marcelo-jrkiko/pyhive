import os
import sys
import io
import builtins
from pathlib import Path

# File access restriction module
__allowed_path__ = "__SANDBOX_DIR__"
__chaquopy_path__ = "__CHAQUOPY_DIR__"
__output_dir__ = "__OUTPUT_DIR__"
__allowed_paths__ = [p for p in (__allowed_path__, __chaquopy_path__, __output_dir__) if p]

def _check_path(path, operation="read"):
    '''Verify that path is within the sandbox'''
    try:
        abs_path = os.path.realpath(path)
        allowed_paths = [os.path.realpath(p) for p in __allowed_paths__ if p]

        for allowed in allowed_paths:
            # Compare by path segments to avoid prefix bypasses like /sandbox-evil.
            common = os.path.commonpath([abs_path, allowed])
            if common == allowed:
                return abs_path

        allowed_display = ", ".join(allowed_paths)
        raise PermissionError(
            f"Access denied: Cannot {operation} '{path}'. "
            f"Only files in [{allowed_display}] are accessible."
        )
    except PermissionError:
        raise
    except Exception as e:
        raise PermissionError(f"Path validation failed: {str(e)}")

# Keep originals so wrapper can restore interpreter state after execution.
_original_builtin_open = builtins.open
_original_io_open = io.open
_original_os_open = os.open
_original_os_listdir = os.listdir
_original_os_scandir = os.scandir
_original_os_stat = os.stat
_original_os_remove = os.remove
_original_os_unlink = os.unlink
_original_os_mkdir = os.mkdir
_original_os_makedirs = os.makedirs
_original_os_rmdir = os.rmdir
_original_os_rename = os.rename
_original_os_replace = os.replace

def restricted_open(file, mode='r', *args, **kwargs):
    if isinstance(file, int):
        return _original_builtin_open(file, mode, *args, **kwargs)
    validated_path = _check_path(file, "access")
    return _original_builtin_open(validated_path, mode, *args, **kwargs)

def restricted_os_open(path, flags, *args, **kwargs):
    if isinstance(path, int):
        return _original_os_open(path, flags, *args, **kwargs)
    validated_path = _check_path(path, "access")
    return _original_os_open(validated_path, flags, *args, **kwargs)

def restricted_listdir(path='.'):
    validated_path = _check_path(path, "list")
    return _original_os_listdir(validated_path)

def restricted_scandir(path='.'):
    validated_path = _check_path(path, "list")
    return _original_os_scandir(validated_path)

def restricted_stat(path, *args, **kwargs):
    if isinstance(path, int):
        return _original_os_stat(path, *args, **kwargs)
    validated_path = _check_path(path, "stat")
    return _original_os_stat(validated_path, *args, **kwargs)

def restricted_remove(path, *args, **kwargs):
    validated_path = _check_path(path, "remove")
    return _original_os_remove(validated_path, *args, **kwargs)

def restricted_unlink(path, *args, **kwargs):
    validated_path = _check_path(path, "unlink")
    return _original_os_unlink(validated_path, *args, **kwargs)

def restricted_mkdir(path, *args, **kwargs):
    validated_path = _check_path(path, "mkdir")
    return _original_os_mkdir(validated_path, *args, **kwargs)

def restricted_makedirs(name, *args, **kwargs):
    validated_path = _check_path(name, "makedirs")
    return _original_os_makedirs(validated_path, *args, **kwargs)

def restricted_rmdir(path, *args, **kwargs):
    validated_path = _check_path(path, "rmdir")
    return _original_os_rmdir(validated_path, *args, **kwargs)

def restricted_rename(src, dst, *args, **kwargs):
    validated_src = _check_path(src, "rename")
    validated_dst = _check_path(dst, "rename")
    return _original_os_rename(validated_src, validated_dst, *args, **kwargs)

def restricted_replace(src, dst, *args, **kwargs):
    validated_src = _check_path(src, "replace")
    validated_dst = _check_path(dst, "replace")
    return _original_os_replace(validated_src, validated_dst, *args, **kwargs)

def restore_restrictions():
    builtins.open = _original_builtin_open
    io.open = _original_io_open
    os.open = _original_os_open
    os.listdir = _original_os_listdir
    os.scandir = _original_os_scandir
    os.stat = _original_os_stat
    os.remove = _original_os_remove
    os.unlink = _original_os_unlink
    os.mkdir = _original_os_mkdir
    os.makedirs = _original_os_makedirs
    os.rmdir = _original_os_rmdir
    os.rename = _original_os_rename
    os.replace = _original_os_replace

# Monkey patch selected file APIs for the current script execution.
builtins.open = restricted_open
io.open = restricted_open
os.open = restricted_os_open
os.listdir = restricted_listdir
os.scandir = restricted_scandir
os.stat = restricted_stat
os.remove = restricted_remove
os.unlink = restricted_unlink
os.mkdir = restricted_mkdir
os.makedirs = restricted_makedirs
os.rmdir = restricted_rmdir
os.rename = restricted_rename
os.replace = restricted_replace
