package module;

import org.apache.commons.lang.text.StrBuilder;

import java.util.ArrayList;
import java.util.List;

import Utils.XmlCode;

/**
 * Created by 11324.
 * Date: 2020/8/13
 */
public class ArrayString extends AndroidString {

    private List<String> items;

    public ArrayString(String key, List<String> items) {
        this.setKey(key);
        this.items = items;
    }

    @Override
    public void encodeXmlValue(AndroidString srcStr) {
        for (int i = 0; i < items.size(); i++) {
            int spCount = XmlCode.getTailSpaceCount(((ArrayString) srcStr).getItems().get(i));
            items.set(i, XmlCode.encode(items.get(i), spCount));
        }
    }

    @Override
    public void decodeXmlValue() {
        for (int i = 0; i < items.size(); i++) {
            items.set(i, XmlCode.decode(items.get(i)));
        }
    }

    public List<String> getItems() {
        return items;
    }

    public void setItems(List<String> items) {
        this.items = items;
    }

    public ArrayString(ArrayString string) {
        this(string.getKey(), new ArrayList<>(string.items));
    }

    @Override
    public String getValue() {
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            String value = items.get(i);
            value = value.replaceAll("\n", "");
            stringBuilder.append(value);
            if (i != items.size() - 1) {
                stringBuilder.append("\n");
            }
        }
        return stringBuilder.toString();
    }

    @Override
    public String toString() {
        StrBuilder sb = new StrBuilder("<string-array name=\"" + getKey() + "\">\n");

        for (String item : items) {
            sb.appendln("\t\t<item>" + item + "</item>");
        }
        sb.append("\t</string-array>");
        return sb.toString();

    }
}
