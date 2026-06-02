package org.enzo.autojjs;

public final class JJSText {
    public static final int MAX_NUMBER = 100000;

    private static final String[] UNIDADES = {
        "", "UM", "DOIS", "TRÊS", "QUATRO", "CINCO", "SEIS", "SETE", "OITO",
        "NOVE", "DEZ", "ONZE", "DOZE", "TREZE", "QUATORZE", "QUINZE",
        "DEZESSEIS", "DEZESSETE", "DEZOITO", "DEZENOVE"
    };

    private static final String[] DEZENAS = {
        "", "", "VINTE", "TRINTA", "QUARENTA", "CINQUENTA", "SESSENTA",
        "SETENTA", "OITENTA", "NOVENTA"
    };

    private static final String[] CENTENAS = {
        "", "CEM", "DUZENTOS", "TREZENTOS", "QUATROCENTOS", "QUINHENTOS",
        "SEISCENTOS", "SETECENTOS", "OITOCENTOS", "NOVECENTOS"
    };

    private JJSText() {
    }

    public static String gerar(int n) {
        return numeroPorExtenso(n) + "!";
    }

    public static String numeroPorExtenso(int n) {
        if (n < 0 || n > MAX_NUMBER) {
            throw new IllegalArgumentException("Numero fora do limite");
        }
        if (n == 0) {
            return "ZERO";
        }

        int milhares = n / 1000;
        int resto = n % 1000;
        StringBuilder out = new StringBuilder();

        if (milhares > 0) {
            out.append(milhares == 1 ? "MIL" : grupo(milhares) + " MIL");
        }
        if (resto > 0) {
            if (out.length() > 0) {
                out.append(" E ");
            }
            out.append(grupo(resto));
        }
        return out.toString();
    }

    private static String grupo(int n) {
        if (n == 0) {
            return "";
        }

        int centena = n / 100;
        int resto = n % 100;
        StringBuilder out = new StringBuilder();

        if (centena > 0) {
            out.append(centena == 1 && resto > 0 ? "CENTO" : CENTENAS[centena]);
        }
        if (resto > 0) {
            if (out.length() > 0) {
                out.append(" E ");
            }
            if (resto < 20) {
                out.append(UNIDADES[resto]);
            } else {
                int dezena = resto / 10;
                int unidade = resto % 10;
                out.append(DEZENAS[dezena]);
                if (unidade > 0) {
                    out.append(" E ").append(UNIDADES[unidade]);
                }
            }
        }
        return out.toString();
    }
}
