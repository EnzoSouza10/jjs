[app]
title = AUTO JJS
package.name = autojjs
package.domain = org.enzo
source.dir = .
source.include_exts = py,kv,png,jpg,ico,xml
source.exclude_dirs = .git,.github,.buildozer,bin,build,dist,__pycache__
version = 4.4.0
requirements = python3,kivy,pyjnius
orientation = portrait
fullscreen = 0
android.permissions = VIBRATE, android.permission.SYSTEM_ALERT_WINDOW
android.api = 35
android.minapi = 24
android.ndk_api = 24
android.archs = arm64-v8a
android.accept_sdk_license = True
android.add_src = android_src
android.add_resources = android_res/xml:xml
android.add_compile_options = "sourceCompatibility = 1.8", "targetCompatibility = 1.8"
p4a.hook = p4a_manifest_hook.py

[buildozer]
log_level = 2
warn_on_root = 1
