package com.github.t1.deployer.app.html.builder;

import static com.github.t1.deployer.app.html.builder.Tag.*;
import lombok.*;

import com.github.t1.deployer.app.html.builder.Table.Cell.CellBuilder;
import com.github.t1.deployer.app.html.builder.Tag.TagBuilder;

@Value
@EqualsAndHashCode(callSuper = true)
public class Table extends DelegateComponent {
    public static TableBuilder table() {
        return new TableBuilder();
    }

    public static CellBuilder cell() {
        return new CellBuilder();
    }

    public static class TableBuilder {
        private final TagBuilder tag = tag("table").classes("table");

        public TableBuilder id(String id) {
            tag.id(id);
            return this;
        }

        public TableBuilder row(CellBuilder... cells) {
            TagBuilder row = tag("tr");
            for (CellBuilder cell : cells) {
                row.body(cell);
            }
            tag.body(row.build());
            return this;
        }

        public TableBuilder row(Cell... cells) {
            TagBuilder row = tag("tr");
            for (Cell cell : cells) {
                row.body(cell);
            }
            tag.body(row.build());
            return this;
        }

        public Table build() {
            return new Table(tag.build());
        }
    }

    @Value
    @EqualsAndHashCode(callSuper = true)
    public static class Cell extends DelegateComponent {
        public static class CellBuilder extends ComponentBuilder {
            private final TagBuilder tag = tag("td").multiline();

            public CellBuilder id(String id) {
                tag.id(id);
                return this;
            }

            public CellBuilder body(ComponentBuilder body) {
                return body(body.build());
            }

            public CellBuilder body(Component body) {
                tag.body(body);
                return this;
            }

            public CellBuilder title(String title) {
                tag.attr("title", title);
                return this;
            }

            public CellBuilder colspan(int i) {
                tag.attr("colspan", Integer.toString(i));
                return this;
            }

            @Override
            public Cell build() {
                return new Cell(tag.build());
            }
        }

        private Cell(Tag tag) {
            super(tag);
        }
    }

    private Table(Tag tag) {
        super(tag);
    }

    @Override
    public boolean isMultiLine() {
        return true;
    }
}
