package Utils;

import java.util.regex.Pattern;

/**
 * Created by 11324.
 * Date: 2020/8/13
 */
public class XmlCode {
    public String raw;
    public String escapeString;
    public String decimalString;

    public XmlCode(String raw, String escapeString, String decimalString) {
        this.raw = raw;
        this.decimalString = decimalString;
        this.escapeString = escapeString;
    }

    public static void main(String[] args) {
        String r = "123 abc \\':&nbsp;";

        System.out.println(r);
        String rr = decode(r);
        System.out.println(rr);
        System.out.println(encode(rr, getTailSpaceCount(rr)));
    }

    public static int getTailSpaceCount(String s) {
        char[] cs = s.toCharArray();
        int i;
        for (i = cs.length - 1; i >= 0; i--) {
            if (cs[i] != ' ' && cs[i] != 160) break;
        }
        return cs.length - i - 1;
    }

    public static String encode(String s, int tailSpaceCount) {
        for (XmlCode code : CODES) {
            if (code.raw.equals(" ")) {
                continue;
            }
            s = s.replaceAll(code.raw, code.decimalString);
        }

        //单引号优化
        s = s.replaceAll("'", "\\\\'");

        //结尾空格
        for (int j = 0; j < tailSpaceCount; j++) {
            s += "&nbsp;";
        }
        return s;
    }

    public static String decode(String s) {
        if (s == null) return null;
        Pattern p = Pattern.compile("&#?.+;");

        for (XmlCode code : CODES) {
            if (p.matcher(s).find()) {
                s = s.replaceAll(code.decimalString, code.raw);
                s = s.replaceAll(code.escapeString, code.raw);
            } else {
                break;
            }
        }
        s = s.replaceAll("\\\\'", "'");
        return s;
    }

    public static final XmlCode[] CODES = new XmlCode[]{
            //new XmlCode("\"", "&quot;", "&#34;"),
            new XmlCode(" ", "&nbsp;", "&#160;"),
            new XmlCode("&", "&amp;", "&#38;"),
    };
}
