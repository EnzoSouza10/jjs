from __future__ import annotations

from pathlib import Path
import xml.etree.ElementTree as ET


ANDROID_NS = "http://schemas.android.com/apk/res/android"
ANDROID_NAME = f"{{{ANDROID_NS}}}name"
ANDROID_ALLOW_BACKUP = f"{{{ANDROID_NS}}}allowBackup"
ANDROID_USES_CLEARTEXT = f"{{{ANDROID_NS}}}usesCleartextTraffic"

ET.register_namespace("android", ANDROID_NS)


def after_apk_build(toolchain) -> None:
    services_path = Path(__file__).with_name("android_manifest_application.xml")

    fragment = ET.fromstring(
        f'<manifest-fragment xmlns:android="{ANDROID_NS}">'
        f"{services_path.read_text(encoding='utf-8')}"
        "</manifest-fragment>"
    )

    for manifest_path in (Path("src/main/AndroidManifest.xml"), Path("AndroidManifest.xml")):
        if manifest_path.exists():
            inject_services(manifest_path, fragment)

    strings_path = Path("src/main/res/values/strings.xml")
    if strings_path.exists():
        inject_accessibility_description(strings_path)


def inject_services(manifest_path: Path, services_fragment: ET.Element) -> None:
    manifest = ET.parse(manifest_path)
    root = manifest.getroot()
    application = root.find("application")
    if application is None:
        raise RuntimeError(f"{manifest_path} has no <application> tag")

    application.set(ANDROID_ALLOW_BACKUP, "false")
    application.set(ANDROID_USES_CLEARTEXT, "false")

    for service in services_fragment.findall("service"):
        service_name = service.attrib.get(ANDROID_NAME)
        if not service_name:
            continue
        for existing in application.findall("service"):
            if existing.attrib.get(ANDROID_NAME) == service_name:
                application.remove(existing)
        application.append(ET.fromstring(ET.tostring(service, encoding="unicode")))

    manifest.write(manifest_path, encoding="utf-8", xml_declaration=True)


def inject_accessibility_description(strings_path: Path) -> None:
    strings = ET.parse(strings_path)
    root = strings.getroot()
    name = "accessibility_service_description"
    text = (
        "Permite que o AUTO JJS, somente quando voce tocar no menu flutuante, "
        "escreva o JJS atual no campo em foco e tente tocar no botao Enviar. "
        "O app nao usa internet nem coleta dados."
    )

    for item in root.findall("string"):
        if item.attrib.get("name") == name:
            item.text = text
            break
    else:
        item = ET.SubElement(root, "string", {"name": name})
        item.text = text

    strings.write(strings_path, encoding="utf-8", xml_declaration=True)
