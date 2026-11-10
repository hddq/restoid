#!/usr/bin/env python3
"""
Restoid Performance & Scroll Benchmark Tool

Measures frame rendering performance, jank percentage, latency percentiles,
and dropped frames across key app screens using Android HWUI dumpsys gfxinfo.

Screens supported:
  - home             : Home screen snapshots list
  - applist          : Tasks -> Backup Applications list (all installed apps & icons)
  - snapshot_details : Snapshot details with backed up apps & metadata
  - all (default)    : Runs benchmarks across all 3 key screens sequentially

Usage:
    ./scripts/benchmark.sh [options]
    python3 scripts/benchmark_scroll.py [options]

Options:
    -b, --build             Build and install the specified variant before benchmarking
    -v, --variant           Build variant: 'benchmark' (default), 'debug', or 'release'
    --screen SCREEN         Screen to benchmark: 'all' (default), 'home', 'applist', 'snapshot_details'
    -p, --pkg               Override package name (defaults to variant's application id)
    -s, --swipes            Number of simulated scroll gestures per screen (default: 8)
    --save FILE             Save benchmark results to a JSON file
    --compare FILE          Compare current run with a previously saved JSON benchmark
    --delay SECONDS         Delay between scroll swipes (default: 0.35)
"""

import argparse
import json
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PROJECT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

VARIANT_MAP = {
    "benchmark": {
        "pkg": "io.github.hddq.restoid.benchmark",
        "gradle_task": "assembleBenchmark",
        "apk_dir": "app/build/outputs/apk/benchmark",
    },
    "debug": {
        "pkg": "io.github.hddq.restoid.debug",
        "gradle_task": "assembleDebug",
        "apk_dir": "app/build/outputs/apk/debug",
    },
    "release": {
        "pkg": "io.github.hddq.restoid",
        "gradle_task": "assembleRelease",
        "apk_dir": "app/build/outputs/apk/release",
    },
}

TARGET_DEVICE = None

def run_cmd(cmd, cwd=None, check=True):
    res = subprocess.run(cmd, cwd=cwd, capture_output=True, text=True)
    if check and res.returncode != 0:
        print(f"Error running {' '.join(cmd)}:\n{res.stderr.strip()}", file=sys.stderr)
        sys.exit(res.returncode)
    return res.stdout.strip()

def run_adb(args, check=True):
    cmd = ["adb"]
    if TARGET_DEVICE:
        cmd += ["-s", TARGET_DEVICE]
    return run_cmd(cmd + args, check=check)

def get_connected_device():
    global TARGET_DEVICE
    out = run_cmd(["adb", "devices"])
    lines = out.splitlines()[1:]
    devices = [line.split()[0] for line in lines if "\tdevice" in line]
    if not devices:
        print("Error: No ADB device connected. Please connect your phone via ADB.", file=sys.stderr)
        sys.exit(1)
    serial_env = os.environ.get("ANDROID_SERIAL")
    if serial_env:
        if serial_env in devices:
            TARGET_DEVICE = serial_env
        else:
            print(f"Error: Specified ANDROID_SERIAL '{serial_env}' not found among connected online devices: {devices}", file=sys.stderr)
            sys.exit(1)
    else:
        TARGET_DEVICE = devices[0]
    model = run_adb(["shell", "getprop", "ro.product.model"], check=False)
    abi = run_adb(["shell", "getprop", "ro.product.cpu.abi"], check=False)
    return TARGET_DEVICE, model, abi

def wake_device():
    run_adb(["shell", "input", "keyevent", "KEYCODE_WAKEUP"], check=False)
    run_adb(["shell", "wm", "dismiss-keyguard"], check=False)

def get_screen_size():
    out = run_adb(["shell", "wm", "size"], check=False)
    override = re.search(r"Override size:\s*(\d+)x(\d+)", out)
    if override:
        return int(override.group(1)), int(override.group(2))
    match = re.search(r"(\d+)x(\d+)", out)
    if match:
        return int(match.group(1)), int(match.group(2))
    return 1080, 2400

def find_best_apk(apk_dir, device_abi):
    if not os.path.isabs(apk_dir):
        apk_dir = os.path.join(PROJECT_DIR, apk_dir)
    if not os.path.exists(apk_dir):
        return None
    apks = [os.path.join(apk_dir, f) for f in os.listdir(apk_dir) if f.endswith(".apk") and "unsigned" not in f]
    if not apks:
        return None
    for apk in apks:
        if device_abi in os.path.basename(apk):
            return apk
    for apk in apks:
        if "universal" in os.path.basename(apk):
            return apk
    return apks[0]

def check_benchmark_variant_exists():
    gradle_file = os.path.join(PROJECT_DIR, "app", "build.gradle.kts")
    if os.path.exists(gradle_file):
        try:
            with open(gradle_file, "r") as f:
                content = f.read()
                return 'create("benchmark")' in content or 'getByName("benchmark")' in content
        except Exception:
            pass
    return False

def build_and_install(variant, device_abi):
    if variant == "benchmark" and not check_benchmark_variant_exists():
        print("Warning: 'benchmark' variant not found in app/build.gradle.kts. Falling back to 'debug'.", file=sys.stderr)
        variant = "debug"

    info = VARIANT_MAP.get(variant)
    if not info:
        print(f"Unknown variant '{variant}'. Choose from: {list(VARIANT_MAP.keys())}", file=sys.stderr)
        sys.exit(1)

    print(f"--> Building {variant} APK (./gradlew {info['gradle_task']})...")
    run_cmd(["./gradlew", info["gradle_task"]], cwd=PROJECT_DIR)

    apk_path = find_best_apk(info["apk_dir"], device_abi)
    if not apk_path:
        print(f"Error: Could not find built APK in {info['apk_dir']}", file=sys.stderr)
        sys.exit(1)

    print(f"--> Installing {os.path.basename(apk_path)}...")
    run_adb(["install", "-r", apk_path])

    pkg = info["pkg"]
    run_adb(["shell", "appops", "set", pkg, "MANAGE_EXTERNAL_STORAGE", "allow"], check=False)
    run_adb(["shell", "pm", "grant", pkg, "android.permission.POST_NOTIFICATIONS"], check=False)
    print("--> Permissions granted.")

def reset_gfxinfo(pkg):
    run_adb(["shell", "dumpsys", "gfxinfo", pkg, "reset"], check=False)

def collect_gfxinfo(pkg):
    return run_adb(["shell", "dumpsys", "gfxinfo", pkg], check=False)

def parse_gfxinfo(output):
    stats = {}
    total_frames = re.search(r"Total frames rendered:\s+(\d+)", output)
    janky_frames = re.search(r"Janky frames:\s+(\d+)\s+\(([\d\.]+)%\)", output)
    p50 = re.search(r"50th percentile:\s+(\d+)ms", output)
    p90 = re.search(r"90th percentile:\s+(\d+)ms", output)
    p95 = re.search(r"95th percentile:\s+(\d+)ms", output)
    p99 = re.search(r"99th percentile:\s+(\d+)ms", output)
    missed_vsync = re.search(r"Number Missed Vsync:\s+(\d+)", output)
    slow_ui = re.search(r"Number Slow UI thread:\s+(\d+)", output)

    if total_frames:
        stats["total_frames"] = int(total_frames.group(1))
    if janky_frames:
        stats["janky_frames"] = int(janky_frames.group(1))
        stats["janky_pct"] = float(janky_frames.group(2))
    if p50: stats["p50"] = int(p50.group(1))
    if p90: stats["p90"] = int(p90.group(1))
    if p95: stats["p95"] = int(p95.group(1))
    if p99: stats["p99"] = int(p99.group(1))
    if missed_vsync: stats["missed_vsync"] = int(missed_vsync.group(1))
    if slow_ui: stats["slow_ui"] = int(slow_ui.group(1))

    return stats

# Dynamic UI Inspection Helpers
def dump_ui_hierarchy():
    run_adb(["shell", "uiautomator", "dump", "/sdcard/bench_ui.xml"], check=False)
    out = run_adb(["shell", "cat", "/sdcard/bench_ui.xml"], check=False)
    try:
        return ET.fromstring(out)
    except Exception:
        return None

def get_node_center(node):
    bounds_str = node.attrib.get("bounds", "")
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds_str)
    if m:
        x1, y1, x2, y2 = map(int, m.groups())
        return (x1 + x2) // 2, (y1 + y2) // 2
    return None

def find_node(root, predicate):
    if root is None:
        return None
    for node in root.iter("node"):
        if predicate(node):
            return node
    return None

# Navigation helpers
def nav_to_home(pkg, w, h):
    wake_device()
    run_adb(["shell", "cmd", "statusbar", "collapse"], check=False)
    run_adb(["shell", "am", "start", "-S", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER", "-n", f"{pkg}/io.github.hddq.restoid.MainActivity"], check=False)
    time.sleep(1.8)
    return True

def nav_to_applist(pkg, w, h):
    nav_to_home(pkg, w, h)
    root = dump_ui_hierarchy()

    def is_tasks_fab(node):
        text = (node.attrib.get("text") or "").lower()
        desc = (node.attrib.get("content-desc") or "").lower()
        # Matches common translations: Tasks, Zadania, Aufgaben, Tareas, Tâches, Tarefas, Attività
        if any(k in text or k in desc for k in ["task", "zadania", "aufgab", "tarea", "tâche", "tarefa", "attività", "attivit"]):
            return True
        return False

    tasks_node = find_node(root, is_tasks_fab)
    if tasks_node is None:
        # Search for a clickable control positioned in the bottom-right FAB region
        def is_bottom_fab(node):
            if node.attrib.get("clickable") == "true":
                center = get_node_center(node)
                if center:
                    cx, cy = center
                    if cx > w * 0.5 and int(h * 0.7) < cy < int(h * 0.95):
                        return True
            return False
        tasks_node = find_node(root, is_bottom_fab)

    if tasks_node is not None:
        tx, ty = get_node_center(tasks_node)
    else:
        tx, ty = int(w * 0.84), int(h * 0.855)

    run_adb(["shell", "input", "tap", str(tx), str(ty)], check=False)
    time.sleep(1.5)

    root2 = dump_ui_hierarchy()
    def is_apps_card(node):
        text = (node.attrib.get("text") or "").strip().lower()
        desc = (node.attrib.get("content-desc") or "").strip().lower()
        valid_titles = [
            "applications", "aplikacje", "anwendungen", "aplicaciones",
            "aplicativos", "applicazioni", "приложения", "tất cả ứng dụng",
            "các ứng dụng", "uygulamalar", "aplikasi", "zadaci", "작업",
            "애플리케이션", "应用程序", "アプリ"
        ]
        if text in valid_titles or desc in valid_titles:
            return True
        prefixes = ["application", "aplikac", "anwend", "aplicat", "applicaz", "приложен"]
        return any(text.startswith(p) or desc.startswith(p) for p in prefixes)

    apps_node = find_node(root2, is_apps_card)
    if apps_node is not None:
        ax, ay = get_node_center(apps_node)
    else:
        ax, ay = w // 2, int(h * 0.28)

    run_adb(["shell", "input", "tap", str(ax), str(ay)], check=False)
    time.sleep(2.0)

    # Verify that BackupConfigScreen is loaded and root access is not denied
    root3 = dump_ui_hierarchy()
    if root3 is not None:
        def is_root_denied(node):
            text = (node.attrib.get("text") or "").lower()
            return "root" in text and ("denied" in text or "odmow" in text or "verweigert" in text or "denegado" in text)

        if find_node(root3, is_root_denied):
            print("[-] Error: Root access is denied. Cannot open Applications backup list without root.", file=sys.stderr)
            return False

        def is_in_applist(node):
            text = (node.attrib.get("text") or "").lower()
            desc = (node.attrib.get("content-desc") or "").lower()
            combined = text + " " + desc
            if any(k in combined for k in ["select all", "zaznacz", "alle ausw", "seleccionar", "tout s", "seleziona", "выбрать все"]):
                return True
            if any(part.startswith(("com.", "org.", "io.", "android.")) for part in combined.split()):
                return True
            return False

        if not find_node(root3, is_in_applist):
            print("[-] Error: Applications list failed to open (screen did not transition).", file=sys.stderr)
            return False

    return True

def leave_applist(pkg, w, h):
    run_adb(["shell", "input", "keyevent", "KEYCODE_BACK"], check=False)
    time.sleep(0.5)
    run_adb(["shell", "input", "keyevent", "KEYCODE_BACK"], check=False)
    time.sleep(0.5)

def nav_to_snapshot_details(pkg, w, h):
    nav_to_home(pkg, w, h)
    root = dump_ui_hierarchy()
    # Dynamically find the first snapshot card (8-character hex snapshot ID)
    snap_node = find_node(root, lambda n: bool(re.match(r"^[0-9a-f]{8}$", n.attrib.get("text", ""))))
    if snap_node is None:
        print("[-] Error: No snapshot found on Home screen to open. Prerequisite: repository must contain at least one snapshot.", file=sys.stderr)
        return False

    sx, sy = get_node_center(snap_node)
    run_adb(["shell", "input", "tap", str(sx), str(sy)], check=False)
    time.sleep(2.0)

    # Verify that SnapshotDetailsScreen actually opened
    root2 = dump_ui_hierarchy()
    if root2 is not None:
        def is_details_screen(node):
            desc = (node.attrib.get("content-desc") or "").lower()
            text = (node.attrib.get("text") or "").lower()
            return (
                "forget" in desc or "delete" in desc or "navigate up" in desc
                or "usunię" in desc or "löschen" in desc or "general" in text
                or "metadata" in text or "backed up" in text
            )

        if not find_node(root2, is_details_screen):
            print("[-] Error: Snapshot details screen failed to open.", file=sys.stderr)
            return False

    return True

def leave_snapshot_details(pkg, w, h):
    run_adb(["shell", "input", "keyevent", "KEYCODE_BACK"], check=False)
    time.sleep(0.5)

SCREEN_NAV_MAP = {
    "home": {
        "title": "Home Screen (Snapshots List)",
        "nav": nav_to_home,
        "cleanup": lambda pkg, w, h: None,
    },
    "applist": {
        "title": "Tasks App List (All Installed Apps)",
        "nav": nav_to_applist,
        "cleanup": leave_applist,
    },
    "snapshot_details": {
        "title": "Snapshot Details (Backed Up Apps)",
        "nav": nav_to_snapshot_details,
        "cleanup": leave_snapshot_details,
    },
}

def benchmark_screen(pkg, screen_id, w, h, swipes, delay):
    info = SCREEN_NAV_MAP[screen_id]
    print(f"\n[+] Navigating to: {info['title']}...")
    nav_ok = info["nav"](pkg, w, h)
    if nav_ok is False:
        print(f"[-] Skipping benchmark for {screen_id} due to navigation failure.")
        info["cleanup"](pkg, w, h)
        return {}

    print(f"[+] Resetting graphics metrics for {screen_id}...")
    reset_gfxinfo(pkg)
    time.sleep(0.4)

    print(f"[+] Scrolling {info['title']} ({swipes} swipes)...")
    cx = w // 2
    y_start_down = int(h * 0.75)
    y_end_down = int(h * 0.25)
    y_start_up = int(h * 0.25)
    y_end_up = int(h * 0.75)

    for i in range(swipes):
        if i % 2 == 0:
            run_adb(["shell", "input", "swipe", str(cx), str(y_start_down), str(cx), str(y_end_down), "250"], check=False)
        else:
            run_adb(["shell", "input", "swipe", str(cx), str(y_start_up), str(cx), str(y_end_up), "250"], check=False)
        time.sleep(delay)

    time.sleep(0.4)
    raw_output = collect_gfxinfo(pkg)
    stats = parse_gfxinfo(raw_output)

    info["cleanup"](pkg, w, h)
    return stats

def print_screen_results(title, current, baseline=None):
    print("\n" + "=" * 65)
    print(f"  RESULTS: {title}")
    print("=" * 65)

    if not current:
        print("No stats collected.")
        return

    headers = f"{'Metric':<25} | {'Current':<15}"
    if baseline:
        headers += f" | {'Baseline':<12} | {'Delta':<10}"
    print(headers)
    print("-" * len(headers))

    def format_row(label, key, unit="", lower_is_better=True):
        cur_val = current.get(key)
        cur_str = f"{cur_val} {unit}".strip() if cur_val is not None else "N/A"

        if not baseline:
            print(f"{label:<25} | {cur_str:<15}")
            return

        base_val = baseline.get(key)
        base_str = f"{base_val} {unit}".strip() if base_val is not None else "N/A"

        if cur_val is not None and base_val is not None:
            diff = cur_val - base_val
            sign = "+" if diff > 0 else ""
            if base_val > 0:
                pct = ((cur_val - base_val) / base_val * 100)
                delta_str = f"{sign}{diff:.1f} ({sign}{pct:.1f}%)" if isinstance(cur_val, float) else f"{sign}{diff} ({sign}{pct:.1f}%)"
            elif diff != 0:
                delta_str = f"{sign}{diff:.1f} (new)" if isinstance(cur_val, float) else f"{sign}{diff} (new)"
            else:
                delta_str = "0 (0.0%)"
            if (diff < 0 and lower_is_better) or (diff > 0 and not lower_is_better):
                delta_str = f"\033[92m{delta_str}\033[0m"  # Green
            elif (diff > 0 and lower_is_better) or (diff < 0 and not lower_is_better):
                delta_str = f"\033[91m{delta_str}\033[0m"  # Red
        else:
            delta_str = "N/A"

        print(f"{label:<25} | {cur_str:<15} | {base_str:<12} | {delta_str}")

    format_row("Total Frames", "total_frames", lower_is_better=False)
    format_row("Janky Frames", "janky_frames")
    format_row("Jank Percentage", "janky_pct", "%")
    format_row("Latency 50th (p50)", "p50", "ms")
    format_row("Latency 90th (p90)", "p90", "ms")
    format_row("Latency 95th (p95)", "p95", "ms")
    format_row("Latency 99th (p99)", "p99", "ms")
    format_row("Missed VSYNCs", "missed_vsync")
    format_row("Slow UI Thread Frames", "slow_ui")
    print("=" * 65)

def print_summary_table(all_results):
    print("\n" + "#" * 68)
    print("                  BENCHMARK OVERALL SUMMARY")
    print("#" * 68)
    print(f"{'Screen':<26} | {'Frames':<8} | {'Jank %':<8} | {'p50':<6} | {'p95':<6} | {'Slow UI':<8}")
    print("-" * 68)
    for screen_id, stats in all_results.items():
        title = SCREEN_NAV_MAP.get(screen_id, {}).get("title", screen_id)
        short_title = title.split("(")[0].strip()[:25]
        frames = stats.get("total_frames", "N/A")
        jank = f"{stats.get('janky_pct', 'N/A')}%"
        p50 = f"{stats.get('p50', 'N/A')}ms"
        p95 = f"{stats.get('p95', 'N/A')}ms"
        slow_ui = stats.get("slow_ui", "N/A")
        print(f"{short_title:<26} | {frames:<8} | {jank:<8} | {p50:<6} | {p95:<6} | {slow_ui:<8}")
    print("#" * 68 + "\n")

def main():
    has_benchmark = check_benchmark_variant_exists()
    default_variant = "benchmark" if has_benchmark else "debug"

    parser = argparse.ArgumentParser(description="Restoid Scroll & Performance Benchmark")
    parser.add_argument("-b", "--build", action="store_true", help="Build and install variant before running")
    parser.add_argument("-v", "--variant", choices=["benchmark", "debug", "release"], default=default_variant,
                        help=f"Build variant to test (default: {default_variant})")
    parser.add_argument("--screen", choices=["all", "home", "applist", "snapshot_details"], default="all",
                        help="Screen to benchmark: 'all' (default), 'home', 'applist', 'snapshot_details'")
    parser.add_argument("-p", "--pkg", help="Override target package name")
    parser.add_argument("-s", "--swipes", type=int, default=8, help="Number of scroll swipes per screen (default: 8)")
    parser.add_argument("--delay", type=float, default=0.35, help="Delay between swipes in seconds (default: 0.35)")
    parser.add_argument("--save", help="Path to save benchmark results JSON")
    parser.add_argument("--compare", help="Path to baseline benchmark JSON to compare against")
    args = parser.parse_args()

    dev_id, model, abi = get_connected_device()
    print(f"Connected Device : {model} ({dev_id}, ABI: {abi})")

    variant = args.variant
    if variant == "benchmark" and not has_benchmark and not args.pkg:
        print("Note: 'benchmark' variant not found in app/build.gradle.kts. Defaulting to 'debug'.")
        variant = "debug"

    if args.build:
        build_and_install(variant, abi)

    pkg = args.pkg or VARIANT_MAP[variant]["pkg"]
    print(f"Target Package   : {pkg}")
    print(f"Benchmark Target : screen='{args.screen}', swipes={args.swipes}")

    wake_device()
    w, h = get_screen_size()

    baseline_data = None
    if args.compare and os.path.exists(args.compare):
        with open(args.compare, "r") as f:
            baseline_data = json.load(f)

    screens_to_run = ["home", "applist", "snapshot_details"] if args.screen == "all" else [args.screen]

    all_results = {}
    for scr in screens_to_run:
        stats = benchmark_screen(pkg, scr, w, h, args.swipes, args.delay)
        all_results[scr] = stats
        scr_baseline = baseline_data.get(scr) if (baseline_data and isinstance(baseline_data, dict)) else None
        print_screen_results(SCREEN_NAV_MAP[scr]["title"], stats, scr_baseline)

    if len(screens_to_run) > 1:
        print_summary_table(all_results)

    if args.save:
        failed_screens = [scr for scr in screens_to_run if not all_results.get(scr) or all_results[scr].get("total_frames", 0) == 0]
        if failed_screens:
            print(f"[-] Error: Benchmark failed or incomplete for screen(s): {', '.join(failed_screens)}. Skipping save to avoid corrupting baseline.", file=sys.stderr)
        else:
            with open(args.save, "w") as f:
                json.dump(all_results, f, indent=2)
            print(f"Benchmark results saved to {args.save}")

if __name__ == "__main__":
    main()
