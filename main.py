"""
AUTO JJS Mobile

Versao adaptada para Android/Buildozer com Kivy.

No celular, um app comum nao consegue controlar o teclado globalmente em
outros aplicativos como o pynput fazia no computador. Por isso esta versao
mantem a automacao dentro do app: gera a sequencia, avanca sozinha, copia o
texto atual para a area de transferencia e oferece um menu flutuante para as
funcoes principais.
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


APP_VERSION = "4.0.0-mobile"
MAX_NUMBER = 50000

COLOR_BG = (0.08, 0.09, 0.10, 1)
COLOR_PANEL = (0.13, 0.15, 0.17, 1)
COLOR_CARD = (0.18, 0.20, 0.22, 1)
COLOR_PRIMARY = (0.12, 0.55, 0.78, 1)
COLOR_PRIMARY_DARK = (0.08, 0.38, 0.55, 1)
COLOR_DANGER = (0.72, 0.18, 0.16, 1)
COLOR_TEXT = (0.94, 0.96, 0.97, 1)
COLOR_MUTED = (0.66, 0.72, 0.76, 1)

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
    end_num = NumericProperty(100)
    current_num = NumericProperty(1)
    interval_s = NumericProperty(1.0)
    running = BooleanProperty(False)
    menu_open = BooleanProperty(True)
    status_text = StringProperty("Pronto")

    def __init__(self, **kwargs):
        super().__init__(**kwargs)
        Window.clearcolor = COLOR_BG
        self._timer = None
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
        top = BoxLayout(orientation="vertical", size_hint_y=None, height=dp(78), spacing=dp(4))
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
        top.add_widget(title)
        top.add_widget(subtitle)
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
            height=dp(370),
            pos_hint={"center_x": 0.5, "y": 0.10},
        )
        self.add_widget(self.menu)

        self.status = Label(
            text=self.status_text,
            color=COLOR_MUTED,
            font_size="14sp",
            size_hint_y=None,
            height=dp(24),
        )
        self.menu.add_widget(self.status)

        range_grid = GridLayout(cols=2, spacing=dp(8), size_hint_y=None, height=dp(74))
        self.start_input = self._number_input("1")
        self.end_input = self._number_input("100")
        range_grid.add_widget(self._field("Quantia inicial", self.start_input))
        range_grid.add_widget(self._field("Ate qual quantia", self.end_input))
        self.menu.add_widget(range_grid)

        row = BoxLayout(orientation="horizontal", spacing=dp(8), size_hint_y=None, height=dp(46))
        row.add_widget(self._button("Aplicar", self.apply_range, COLOR_PRIMARY_DARK))
        row.add_widget(self._button("Copiar atual", self.copy_current, COLOR_PRIMARY_DARK))
        self.menu.add_widget(row)

        nav = BoxLayout(orientation="horizontal", spacing=dp(8), size_hint_y=None, height=dp(46))
        nav.add_widget(self._button("Anterior", self.previous_jj, COLOR_CARD))
        nav.add_widget(self._button("Proximo", self.next_jj, COLOR_CARD))
        nav.add_widget(self._button("Reset", self.reset_jj, COLOR_CARD))
        self.menu.add_widget(nav)

        auto = BoxLayout(orientation="horizontal", spacing=dp(10), size_hint_y=None, height=dp(48))
        self.auto_switch = Switch(active=False, size_hint_x=None, width=dp(70))
        self.auto_switch.bind(active=self._on_auto_switch)
        auto.add_widget(self.auto_switch)
        auto.add_widget(Label(text="JJS automatico", color=COLOR_TEXT, font_size="17sp", halign="left"))
        self.menu.add_widget(auto)

        speed = BoxLayout(orientation="vertical", spacing=dp(4), size_hint_y=None, height=dp(70))
        self.interval_label = Label(
            text="Intervalo: 1.0s",
            color=COLOR_MUTED,
            font_size="14sp",
            size_hint_y=None,
            height=dp(22),
        )
        self.interval_slider = Slider(min=0.3, max=5.0, value=self.interval_s)
        self.interval_slider.bind(value=self._on_interval)
        speed.add_widget(self.interval_label)
        speed.add_widget(self.interval_slider)
        self.menu.add_widget(speed)

        self.menu.add_widget(self._button("Copiar lista do intervalo", self.copy_range, COLOR_PRIMARY))

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
        self.interval_label.text = f"Intervalo: {self.interval_s:.1f}s"
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
                lines.append(f"[color=1ed0ff][b]{n:>5}  {item}[/b][/color]")
            else:
                lines.append(f"{n:>5}  {item}")
        self.history.text = "\n".join(lines)


class AutoJJSApp(App):
    title = "AUTO JJS"

    def build(self):
        return AutoJJSMobile()


if __name__ == "__main__":
    AutoJJSApp().run()
