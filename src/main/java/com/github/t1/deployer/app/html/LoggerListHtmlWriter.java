package com.github.t1.deployer.app.html;

import static com.github.t1.deployer.app.html.DeployerComponents.*;
import static com.github.t1.deployer.app.html.DeployerPage.*;
import static com.github.t1.deployer.app.html.LoggerHtmlWriter.*;
import static com.github.t1.deployer.app.html.builder.ButtonGroup.*;
import static com.github.t1.deployer.app.html.builder.Compound.*;
import static com.github.t1.deployer.app.html.builder.Form.*;
import static com.github.t1.deployer.app.html.builder.Input.*;
import static com.github.t1.deployer.app.html.builder.SizeVariation.*;
import static com.github.t1.deployer.app.html.builder.Static.*;
import static com.github.t1.deployer.app.html.builder.Table.*;
import static com.github.t1.deployer.app.html.builder.Tags.*;

import java.net.URI;
import java.util.*;

import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.Provider;

import com.github.t1.deployer.app.Loggers;
import com.github.t1.deployer.app.html.builder.*;
import com.github.t1.deployer.app.html.builder.Table.*;
import com.github.t1.deployer.app.html.builder.Tags.AppendingComponent;
import com.github.t1.deployer.model.LoggerConfig;
import com.github.t1.log.LogLevel;

@Provider
public class LoggerListHtmlWriter extends TextHtmlListMessageBodyWriter<LoggerConfig> {
    private static final Cell ADD_LOGGER_ROW = cell().colspan(3).body(link(new AppendingComponent<URI>() {
        @Override
        protected URI contentFrom(BuildContext out) {
            return Loggers.newLogger(out.get(UriInfo.class));
        }
    }).body(text("+")).build()).build();

    private static final Component TABLE = new Component() {
        @Override
        public void writeTo(BuildContext out) {
            TableBuilder table = table();
            @SuppressWarnings("unchecked")
            List<LoggerConfig> loggers = out.get(List.class);
            Collections.sort(loggers);
            UriInfo uriInfo = out.get(UriInfo.class);
            int i = 0;
            for (LoggerConfig logger : loggers) {
                URI action = Loggers.path(uriInfo, logger);
                table.row( //
                        cell().body(link(action).body(text(logger.getCategory())).build()).build(), //
                        cell().body(levelForm(logger.getLevel(), action)).build(), //
                        cell().body(deleteButton(i++, action)).build() //
                );
            }
            table.row(ADD_LOGGER_ROW);
            table.build().writeTo(out);
        }

        private Form levelForm(LogLevel level, URI action) {
            return form().action(action).body(levelSelect(level)).build();
        }

        private Component deleteButton(int i, URI action) {
            String formId = "delete-" + i;
            return compound( //
                    form(formId).action(action).body(hiddenAction("delete")).build(), //
                    buttonGroup().button(remove(formId, XS)).build() //
            ).build();
        }
    };

    private static final DeployerPage PAGE = panelPage() //
            .title(text("Loggers")) //
            .body(TABLE) //
            .build();

    @Override
    protected void prepare(BuildContext buildContext) {
        buildContext.put(Navigation.LOGGERS);
    }

    @Override
    protected Component component() {
        return PAGE;
    }
}
