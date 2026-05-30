"""
AUTO JJS Mobile

Versao adaptada para Android/Buildozer com Kivy.

No celular, o envio em outros apps depende de permissoes explicitas do
Android: sobrepor apps para mostrar o menu flutuante e Acessibilidade para
inserir texto no campo em foco.
"""

from __future__ import annotations

from functools import lru_cache

from kivy.app import App
from kivy.clock import Clock
from kivy.core.clipboard import Clipboard
from kivy.core.window import Window
from kivy.graphics import Color, RoundedRectangle
from kivy.metrics import dp
from kivy.properties import BooleanProperty, NumericProperty, StringProperty
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.button import Button
from kivy.uix.floatlayout import FloatLayout
from kivy.uix.gridlayout import GridLayout
from kivy.uix.label import Label
from kivy.uix.progressbar import ProgressBar
from kivy.uix.scrollview import ScrollView
from kivy.uix.slider import Slider
from kivy.uix.switch import Switch
from kivy.uix.textinput import TextInput
from kivy.uix.togglebutton import ToggleButton

try:
    from jnius import autoclass
except Exception:  # pragma: no cover - disponivel apenas no Android empacotado
    autoclass = None


APP_VERSION = "4.3.0-mobile"
MAX_NUMBER = 50000
DEFAULT_INTERVAL_S = 2.2
MIN_INTERVAL_S = 1.8

COLOR_BG = (0.02, 0.02, 0.025, 1)
COLOR_PANEL = (0.055, 0.045, 0.05, 1)
COLOR_CARD = (0.105, 0.085, 0.09, 1)
COLOR_PRIMARY = (0.86, 0.06, 0.10, 1)
COLOR_PRIMARY_DARK = (0.48, 0.02, 0.05, 1)
COLOR_DANGER = (0.72, 0.03, 0.06, 1)
COLOR_TEXT = (0.98, 0.95, 0.94, 1)
COLOR_MUTED = (0.78, 0.60, 0.60, 1)

UNIDADES = (
    "",
    "UM",
    "DOIS",
    "TRES",
    "QUATRO",
    "CINCO",
    "SEIS",
    "SETE",
    "OITO",
    "NOVE",
    "DEZ",
    "ONZE",
    "DOZE",
    "TREZE",
    "QUATORZE",
    "QUINZE",
    "DEZESSEIS",
    "DEZESSETE",
    "DEZOITO",
    "DEZENOVE",
)
DEZENAS = (
    "",
    "",
    "VINTE",
    "TRINTA",
    "QUARENTA",
    "CINQUENTA",
    "SESSENTA",
    "SETENTA",
    "OITENTA",
    "NOVENTA",
)
CENTENAS = (
    "",
    "CEM",
    "DUZENTOS",
    "TREZENTOS",
    "QUATROCENTOS",
    "QUINHENTOS",
    "SEISCENTOS",
    "SETECENTOS",
    "OITOCENTOS",
    "NOVECENTOS",
)


@lru_cache(maxsize=2048)
def _grupo(n: int) -> str:
    if n == 0:
        return ""

    partes: list[str] = []
    centena, resto = divmod(n, 100)

    if centena:
        partes.append("CENTO" if centena == 1 and resto else CENTENAS[centena])

    if resto:
        if centena:
            partes.append("E")
        if resto < 20:
            partes.append(UNIDADES[resto])
        else:
            dezena, unidade = divmod(resto, 10)
            partes.append(DEZENAS[dezena])
            if unidade:
                partes.extend(("E", UNIDADES[unidade]))

    return " ".join(partes)


@lru_cache(maxsize=MAX_NUMBER)
def numero_por_extenso(n: int) -> str:
    if not 0 <= n <= MAX_NUMBER:
        raise ValueError(f"O numero precisa estar entre 0 e {MAX_NUMBER}.")
    if n == 0:
        return "ZERO"

    milhares, resto = divmod(n, 1000)
    partes: list[str] = []

    if milhares:
        partes.append("MIL" if milhares == 1 else f"{_grupo(milhares)} MIL")
    if resto:
        if milhares:
            partes.append("E")
        partes.append(_grupo(resto))

    return " ".join(partes)


def gerar_jj(n: int) -> str:
    return f"{numero_por_extenso(n)}!"


class AndroidBridge:
    def __init__(self):
        self.available = False
        self.activity = None
        self.bridge = None
        if autoclass is None:
            return
        try:
            PythonActivity = autoclass("org.kivy.android.PythonActivity")
            self.activity = PythonActivity.mActivity
            self.bridge = autoclass("org.enzo.autojjs.AutoJJSBridge")
            self.available = True
        except Exception:
            self.available = False

    def save_config(self, start: int, end: int, current: int, interval: float, auto_click_send: bool) -> bool:
        if not self.available:
            return False
        self.bridge.saveConfig(self.activity, start, end, current, float(interval), bool(auto_click_send))
        return True

    def start_overlay(self) -> bool:
        if not self.available:
            return False
        self.bridge.startOverlay(self.activity)
        return True

    def stop_overlay(self) -> bool:
        if not self.available:
            return False
        self.bridge.stopOverlay(self.activity)
        return True

    def open_overlay_settings(self) -> bool:
        if not self.available:
            return False
        self.bridge.openOverlaySettings(self.activity)
        return True

    def open_accessibility_settings(self) -> bool:
        if not self.available:
            return False
        self.bridge.openAccessibilitySettings(self.activity)
        return True

    def can_draw_overlays(self) -> bool:
        return bool(self.available and self.bridge.canDrawOverlays(self.activity))

    def accessibility_enabled(self) -> bool:
        return bool(self.available and self.bridge.isAccessibilityEnabled(self.activity))


class Card(BoxLayout):
    def __init__(self, bg_color=COLOR_CARD, radius=8, **kwargs):
        super().__init__(**kwargs)
        self.orientation = "vertical"
        self.padding = dp(14)
        self.spacing = dp(10)
        self._bg_color = bg_color
        self._radius = [dp(radius)]
        with self.canvas.before:
            Color(*self._bg_color)
            self._bg = RoundedRectangle(pos=self.pos, size=self.size, radius=self._radius)
        self.bind(pos=self._sync_bg, size=self._sync_bg)

    def _sync_bg(self, *_args):
        self._bg.pos = self.pos
        self._bg.size = self.size


class AutoJJSMobile(FloatLayout):
    start_num = NumericProperty(1)
    end_num = NumericProperty(MAX_NUMBER)
    current_num = NumericProperty(1)
    interval_s = NumericProperty(DEFAULT_INTERVAL_S)
    running = BooleanProperty(False)
    menu_open = BooleanProperty(True)
    status_text = StringProperty("Pronto para mobile")

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        Window.clearcolor = COLOR_BG
        self._timer = None
        self.android = AndroidBridge()
        self._build_ui()
        self._refresh()

    def _build_ui(self) -> None:
        self.main = BoxLayout(
            orientation="vertical",
            padding=[dp(16), dp(14), dp(16), dp(14)],
            spacing=dp(12),
        )
        self.add_widget(self.main)

        self._build_header()
        self._build_preview()
        self._build_history()
        self._build_floating_menu()

    def _build_header(self) -> None:
        top = BoxLayout(orientation="vertical", size_hint_y=None, height=dp(96), spacing=dp(4))
        title = Label(
            text="AUTO JJS",
            color=COLOR_TEXT,
            bold=True,
            font_size="28sp",
            halign="left",
            valign="middle",
        )
        title.bind(size=title.setter("text_size"))
        subtitle = Label(
            text=f"Mobile v{APP_VERSION} - limite {MAX_NUMBER}",
            color=COLOR_MUTED,
            font_size="14sp",
            halign="left",
            valign="middle",
        )
        subtitle.bind(size=subtitle.setter("text_size"))
        credits = Label(
            text="Criador: Xx0iluminati0xX",
            color=COLOR_PRIMARY,
            bold=True,
            font_size="14sp",
            halign="left",
            valign="middle",
        )
        credits.bind(size=credits.setter("text_size"))
        top.add_widget(title)
        top.add_widget(subtitle)
        top.add_widget(credits)
        self.main.add_widget(top)

    def _build_preview(self) -> None:
        preview = Card(size_hint_y=None, height=dp(172))

        self.number_label = Label(
            text="1",
            color=COLOR_PRIMARY,
            bold=True,
            font_size="38sp",
            size_hint_y=None,
            height=dp(48),
        )
        self.text_label = Label(
            text="UM!",
            color=COLOR_TEXT,
            bold=True,
            font_size="28sp",
            halign="center",
            valign="middle",
        )
        self.text_label.bind(size=self.text_label.setter("text_size"))

        self.progress = ProgressBar(max=100, value=0, size_hint_y=None, height=dp(8))
        self.counter_label = Label(
            text="1 / 100",
            color=COLOR_MUTED,
            font_size="14sp",
            size_hint_y=None,
            height=dp(22),
        )

        preview.add_widget(self.number_label)
        preview.add_widget(self.text_label)
        preview.add_widget(self.progress)
        preview.add_widget(self.counter_label)
        self.main.add_widget(preview)

    def _build_history(self) -> None:
        self.history = Label(
            text="",
            color=COLOR_MUTED,
            font_size="16sp",
            halign="left",
            valign="top",
            markup=True,
        )
        self.history.bind(width=lambda *_: setattr(self.history, "text_size", (self.history.width, None)))
        self.history.bind(texture_size=lambda *_: setattr(self.history, "height", self.history.texture_size[1]))

        scroll = ScrollView()
        scroll.add_widget(self.history)
        self.main.add_widget(scroll)

    def _build_floating_menu(self) -> None:
        self.menu_button = ToggleButton(
            text="FECHAR",
            size_hint=(None, None),
            size=(dp(86), dp(52)),
            pos_hint={"right": 0.98, "y": 0.02},
            background_color=COLOR_PRIMARY,
            color=COLOR_TEXT,
            bold=True,
        )
        self.menu_button.bind(on_release=self._toggle_menu)
        self.add_widget(self.menu_button)

        self.menu = Card(
            bg_color=COLOR_PANEL,
            radius=8,
            size_hint=(0.92, None),
            height=dp(600),
            pos_hint={"center_x": 0.5, "y": 0.10},
        )
        self.add_widget(self.menu)

        self.menu_content = BoxLayout(orientation="vertical", spacing=dp(10), size_hint_y=None)
        self.menu_content.bind(minimum_height=self.menu_content.setter("height"))
        menu_scroll = ScrollView(do_scroll_x=False)
        menu_scroll.add_widget(self.menu_content)
        self.menu.add_widget(menu_scroll)

        self.status = Label(
            text=self.status_text,
            color=COLOR_MUTED,
            font_size="14sp",
            size_hint_y=None,
            height=dp(24),
        )
        self.menu_content.add_widget(self.status)

        mode = Label(
            text="Guia rapido: defina o inicio e ate quantos JJS fazer, abra o flutuante e use ESCREVER ou AUTO no app alvo.",
            color=COLOR_MUTED,
            font_size="13sp",
            halign="center",
            valign="middle",
            size_hint_y=None,
            height=dp(38),
        )
        mode.bind(size=mode.setter("text_size"))
        self.menu_content.add_widget(mode)

        range_grid = GridLayout(cols=2, spacing=dp(8), size_hint_y=None, height=dp(74))
        self.start_input = self._number_input("1")
        self.end_input = self._number_input(str(MAX_NUMBER))
        range_grid.add_widget(self._field("Quantia inicial", self.start_input))
        range_grid.add_widget(self._field("Fazer ate o JJS", self.end_input))
        self.menu_content.add_widget(range_grid)

        row = BoxLayout(orientation="horizontal", spacing=dp(8), size_hint_y=None, height=dp(46))
        row.add_widget(self._button("Aplicar", self.apply_range, COLOR_PRIMARY_DARK))
        row.add_widget(self._button("Copiar atual", self.copy_current, COLOR_PRIMARY_DARK))
        self.menu_content.add_widget(row)

        perms = BoxLayout(orientation="horizontal", spacing=dp(8), size_hint_y=None, height=dp(46))
        perms.add_widget(self._button("Perm. flutuante", self.open_overlay_settings, COLOR_CARD))
        perms.add_widget(self._button("Acessibilidade", self.open_accessibility_settings, COLOR_CARD))
        self.menu_content.add_widget(perms)

        overlay = BoxLayout(orientation="horizontal", spacing=dp(8), size_hint_y=None, height=dp(46))
        overlay.add_widget(self._button("Abrir menu flutuante", self.start_overlay_menu, COLOR_PRIMARY))
        overlay.add_widget(self._button("Fechar", self.stop_overlay_menu, COLOR_DANGER))
        self.menu_content.add_widget(overlay)

        nav = BoxLayout(orientation="horizontal", spacing=dp(8), size_hint_y=None, height=dp(46))
        nav.add_widget(self._button("< JJS anterior", self.previous_jj, COLOR_CARD))
        nav.add_widget(self._button("Proximo JJS >", self.next_jj, COLOR_CARD))
        nav.add_widget(self._button("Reset", self.reset_jj, COLOR_CARD))
        self.menu_content.add_widget(nav)

        auto = BoxLayout(orientation="horizontal", spacing=dp(10), size_hint_y=None, height=dp(48))
        self.auto_switch = Switch(active=False, size_hint_x=None, width=dp(70))
        self.auto_switch.bind(active=self._on_auto_switch)
        auto.add_widget(self.auto_switch)
        auto.add_widget(Label(text="Auto interno copia no app", color=COLOR_TEXT, font_size="17sp", halign="left"))
        self.menu_content.add_widget(auto)

        send = BoxLayout(orientation="horizontal", spacing=dp(10), size_hint_y=None, height=dp(48))
        self.send_switch = Switch(active=True, size_hint_x=None, width=dp(70))
        self.send_switch.bind(active=lambda *_: self.sync_android_config())
        send.add_widget(self.send_switch)
        send.add_widget(Label(text="Tentar tocar em Enviar", color=COLOR_TEXT, font_size="17sp", halign="left"))
        self.menu_content.add_widget(send)

        speed = BoxLayout(orientation="vertical", spacing=dp(4), size_hint_y=None, height=dp(70))
        self.interval_label = Label(
            text=f"Intervalo seguro: {self.interval_s:.1f}s",
            color=COLOR_MUTED,
            font_size="14sp",
            size_hint_y=None,
            height=dp(22),
        )
        self.interval_slider = Slider(min=MIN_INTERVAL_S, max=8.0, value=self.interval_s)
        self.interval_slider.bind(value=self._on_interval)
        speed.add_widget(self.interval_label)
        speed.add_widget(self.interval_slider)
        self.menu_content.add_widget(speed)

        self.menu_content.add_widget(self._button("Copiar lista do intervalo", self.copy_range, COLOR_PRIMARY))

        guide = Label(
            text=(
                "[b]Botoes:[/b] Aplicar salva o intervalo; Copiar atual copia o JJS exibido; "
                "Perm. flutuante autoriza o menu sobre outros apps; Acessibilidade permite escrever no campo selecionado; "
                "Abrir menu flutuante mostra os controles externos; Fechar remove o flutuante; "
                "JJS anterior/Proximo navegam sem perder a sequencia; Reset volta ao inicio; "
                "Auto interno copia em ritmo seguro; Tentar tocar em Enviar aciona o botao enviar do app alvo."
            ),
            color=COLOR_MUTED,
            font_size="12sp",
            halign="left",
            valign="top",
            markup=True,
            size_hint_y=None,
            height=dp(108),
        )
        guide.bind(size=guide.setter("text_size"))
        self.menu_content.add_widget(guide)

        credit = Label(
            text="Criador: Xx0iluminati0xX",
            color=COLOR_PRIMARY,
            bold=True,
            font_size="13sp",
            size_hint_y=None,
            height=dp(26),
        )
        self.menu_content.add_widget(credit)

    def _number_input(self, value: str) -> TextInput:
        return TextInput(
            text=value,
            input_filter="int",
            multiline=False,
            font_size="20sp",
            halign="center",
            background_color=COLOR_PANEL,
            foreground_color=COLOR_TEXT,
            cursor_color=COLOR_PRIMARY,
            padding=[dp(8), dp(8), dp(8), dp(8)],
        )

    def _field(self, label: str, widget: TextInput) -> BoxLayout:
        box = BoxLayout(orientation="vertical", spacing=dp(4))
        box.add_widget(Label(text=label, color=COLOR_MUTED, font_size="13sp", size_hint_y=None, height=dp(22)))
        box.add_widget(widget)
        return box

    def _button(self, text: str, callback, color) -> Button:
        btn = Button(
            text=text,
            size_hint_y=None,
            height=dp(46),
            background_color=color,
            color=COLOR_TEXT,
            bold=True,
            font_size="15sp",
        )
        btn.bind(on_release=lambda *_: callback())
        return btn

    def _toggle_menu(self, *_args) -> None:
        self.menu_open = not self.menu_open
        self.menu.opacity = 1 if self.menu_open else 0
        self.menu.disabled = not self.menu_open
        self.menu_button.text = "FECHAR" if self.menu_open else "MENU"

    def _on_interval(self, _slider, value: float) -> None:
        self.interval_s = round(float(value), 1)
        self.interval_label.text = f"Intervalo seguro: {self.interval_s:.1f}s"
        self.sync_android_config()
        if self.running:
            self._restart_timer()

    def _on_auto_switch(self, _switch, active: bool) -> None:
        if active:
            self.start_auto()
        else:
            self.stop_auto("Pausado")

    def _parse_range(self) -> tuple[int, int]:
        start = int(self.start_input.text or "0")
        end = int(self.end_input.text or "0")
        if not 1 <= start <= end <= MAX_NUMBER:
            raise ValueError(f"Use valores de 1 ate {MAX_NUMBER}, com inicial menor ou igual ao final.")
        return start, end

    def _set_status(self, text: str, danger: bool = False) -> None:
        self.status_text = text
        self.status.text = text
        self.status.color = COLOR_DANGER if danger else COLOR_MUTED

    def apply_range(self) -> bool:
        try:
            self.start_num, self.end_num = self._parse_range()
        except ValueError as exc:
            self._set_status(str(exc), danger=True)
            return False

        self.current_num = self.start_num
        self._set_status("Intervalo aplicado")
        self._refresh()
        self.sync_android_config()
        return True

    def start_auto(self) -> None:
        if not self.apply_range():
            self.auto_switch.active = False
            return

        self.running = True
        self._set_status("JJS automatico ligado")
        self.copy_current()
        self._restart_timer()

    def stop_auto(self, message: str = "Parado") -> None:
        self.running = False
        if self._timer is not None:
            self._timer.cancel()
            self._timer = None
        self._set_status(message)

    def _restart_timer(self) -> None:
        if self._timer is not None:
            self._timer.cancel()
        self._timer = Clock.schedule_interval(lambda _dt: self._auto_step(), self.interval_s)

    def _auto_step(self) -> bool:
        if not self.running:
            return False
        self.next_jj(copy=True)
        return True

    def next_jj(self, copy: bool = False) -> None:
        self.current_num = self.current_num + 1 if self.current_num < self.end_num else self.start_num
        self._refresh()
        if copy:
            self.copy_current(silent=True)

    def previous_jj(self) -> None:
        self.current_num = self.current_num - 1 if self.current_num > self.start_num else self.end_num
        self._refresh()

    def reset_jj(self) -> None:
        self.current_num = self.start_num
        self._refresh()
        self._set_status("Sequencia reiniciada")

    def copy_current(self, silent: bool = False) -> None:
        Clipboard.copy(gerar_jj(self.current_num))
        if not silent:
            self._set_status(f"Copiado: {gerar_jj(self.current_num)}")

    def copy_range(self) -> None:
        try:
            start, end = self._parse_range()
        except ValueError as exc:
            self._set_status(str(exc), danger=True)
            return

        Clipboard.copy("\n".join(gerar_jj(n) for n in range(start, end + 1)))
        self._set_status(f"Lista copiada: {start} ate {end}")

    def sync_android_config(self) -> bool:
        auto_click_send = getattr(self, "send_switch", None)
        send_enabled = True if auto_click_send is None else bool(auto_click_send.active)
        return self.android.save_config(
            int(self.start_num),
            int(self.end_num),
            int(self.current_num),
            float(self.interval_s),
            send_enabled,
        )

    def open_overlay_settings(self) -> None:
        if self.android.open_overlay_settings():
            self._set_status("Ative: Permitir sobrepor a outros apps")
        else:
            self._set_status("Permissao flutuante so funciona no APK Android", danger=True)

    def open_accessibility_settings(self) -> None:
        if self.android.open_accessibility_settings():
            self._set_status("Ative o servico AUTO JJS em Acessibilidade")
        else:
            self._set_status("Acessibilidade so funciona no APK Android", danger=True)

    def start_overlay_menu(self) -> None:
        if not self.apply_range():
            return
        self.sync_android_config()
        if not self.android.available:
            self._set_status("Menu flutuante so funciona no APK Android", danger=True)
            return
        if not self.android.can_draw_overlays():
            self.open_overlay_settings()
            return
        if not self.android.accessibility_enabled():
            self.open_accessibility_settings()
            return
        if self.android.start_overlay():
            self._set_status("Menu flutuante ativo")
        else:
            self._set_status("Nao foi possivel abrir o menu flutuante", danger=True)

    def stop_overlay_menu(self) -> None:
        if self.android.stop_overlay():
            self._set_status("Menu flutuante fechado")
        else:
            self._set_status("Menu flutuante so funciona no APK Android", danger=True)

    def _refresh(self) -> None:
        text = gerar_jj(self.current_num)
        self.number_label.text = str(self.current_num)
        self.text_label.text = text
        self.counter_label.text = f"{self.current_num} / {self.end_num}"

        total = max(1, self.end_num - self.start_num)
        self.progress.value = 100 * max(0, self.current_num - self.start_num) / total

        start = max(self.start_num, self.current_num - 4)
        end = min(self.end_num, self.current_num + 8)
        lines = []
        for n in range(start, end + 1):
            item = gerar_jj(n)
            if n == self.current_num:
                lines.append(f"[color=dc1018][b]{n:>5}  {item}[/b][/color]")
            else:
                lines.append(f"{n:>5}  {item}")
        self.history.text = "\n".join(lines)
        if hasattr(self, "android"):
            self.sync_android_config()


class AutoJJSApp(App):
    title = "AUTO JJS"

    def build(self):
        return AutoJJSMobile()


if __name__ == "__main__":
    AutoJJSApp().run()
