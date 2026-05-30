from __future__ import annotations

from pathlib import Path
import xml.etree.ElementTree as ET


ANDROID_NS = "http://schemas.android.com/apk/res/android"
ANDROID_NAME = f"{{{ANDROID_NS}}}name"

ET.register_namespace("android", ANDROID_NS)


def after_apk_build(toolchain) -> None:
    manifest_path = Path("AndroidManifest.xml")
    services_path = Path(__file__).with_name("android_manifest_application.xml")

    manifest = ET.parse(manifest_path)
    root = manifest.getroot()
    application = root.find("application")
    if application is None:
        raise RuntimeError("AndroidManifest.xml has no <application> tag")

    fragment = ET.fromstring(
        f'<manifest-fragment xmlns:android="{ANDROID_NS}">'
        f"{services_path.read_text(encoding='utf-8')}"
        "</manifest-fragment>"
    )

    for service in fragment.findall("service"):
        service_name = service.attrib.get(ANDROID_NAME)
        if not service_name:
            continue
        for existing in application.findall("service"):
            if existing.attrib.get(ANDROID_NAME) == service_name:
                application.remove(existing)
        application.append(service)

    manifest.write(manifest_path, encoding="utf-8", xml_declaration=True)
