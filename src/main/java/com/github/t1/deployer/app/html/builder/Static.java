package com.github.t1.deployer.app.html.builder;

import lombok.*;

@Value
@EqualsAndHashCode(callSuper = true)
public class Static extends Component {
    public static Static nl() {
        return text("\n");
    }

    public static Static textOr(Object text, String nullValue) {
        return new Static((text == null) ? nullValue : text.toString());
    }

    public static Static text(Object text) {
        return new Static(text.toString());
    }

    @NonNull
    private final String text;

    @Override
    public void writeTo(BuildContext out) {
        out.append(text);
    }

    @Override
    public boolean isMultiLine() {
        return text.contains("\n");
    }

    @Override
    public String toString() {
        return "'" + text + "'";
    }
}
