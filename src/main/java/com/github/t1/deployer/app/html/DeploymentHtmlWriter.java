package com.github.t1.deployer.app.html;

import static com.github.t1.deployer.app.html.DeployerPage.*;
import static com.github.t1.deployer.app.html.builder.Button.*;
import static com.github.t1.deployer.app.html.builder.ButtonGroup.*;
import static com.github.t1.deployer.app.html.builder.Compound.*;
import static com.github.t1.deployer.app.html.builder.DescriptionList.*;
import static com.github.t1.deployer.app.html.builder.Form.*;
import static com.github.t1.deployer.app.html.builder.Input.*;
import static com.github.t1.deployer.app.html.builder.SizeVariation.*;
import static com.github.t1.deployer.app.html.builder.Static.*;
import static com.github.t1.deployer.app.html.builder.StyleVariation.*;
import static com.github.t1.deployer.app.html.builder.Table.*;
import static com.github.t1.deployer.app.html.builder.Tags.*;

import java.net.URI;

import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.Provider;

import com.github.t1.deployer.app.*;
import com.github.t1.deployer.app.html.DeployerPage.DeployerPageBuilder;
import com.github.t1.deployer.app.html.builder.*;
import com.github.t1.deployer.app.html.builder.Compound.CompoundBuilder;
import com.github.t1.deployer.app.html.builder.DescriptionList.DescriptionListBuilder;
import com.github.t1.deployer.app.html.builder.Form.FormBuilder;
import com.github.t1.deployer.app.html.builder.Table.TableBuilder;
import com.github.t1.deployer.app.html.builder.Tags.AppendingComponent;
import com.github.t1.deployer.model.*;

@Provider
public class DeploymentHtmlWriter extends TextHtmlMessageBodyWriter<Deployment> {
    private static DeployerPageBuilder page() {
        return deployerPage() //
                .title(new AppendingComponent<String>() {
                    @Override
                    protected String contentFrom(BuildContext out) {
                        Deployment target = out.get(Deployment.class);
                        return target.isNew() ? "Add Deployment" : target.getName().getValue();
                    }
                }) //
                .backLink(new AppendingComponent<URI>() {
                    @Override
                    protected URI contentFrom(BuildContext out) {
                        return Deployments.pathAll(out.get(UriInfo.class));
                    }
                });
    }

    private static final Component AVAILABLE_VERSIONS = //
            new Component() {
                @Override
                public void writeTo(BuildContext out) {
                    UriInfo uriInfo = out.get(UriInfo.class);
                    Deployment deployment = out.get(Deployment.class);
                    Version currentVersion = deployment.getVersion();
                    TableBuilder table = table();
                    int i = 0;
                    for (VersionInfo entry : deployment.getAvailableVersions()) {
                        boolean isCurrent = entry.getVersion().equals(currentVersion);
                        table.row( //
                                cell().body(text(entry.getVersion().getVersion())), //
                                cell().body(redeployButton("redeploy-" + i++, //
                                        deployment.getContextRoot(), entry.getCheckSum(), //
                                        uriInfo, isCurrent)) //
                        );
                    }
                    table.build().writeTo(out);
                }

                private CompoundBuilder redeployButton(String id, ContextRoot contextRoot, CheckSum checkSum,
                        UriInfo uriInfo, boolean isCurrent) {
                    FormBuilder form = form(id);
                    form.action(text(Deployments.path(uriInfo, contextRoot)));
                    form.input(hiddenInput("contextRoot", contextRoot.getValue()));
                    form.input(hiddenInput("checksum", checkSum.hexString()));
                    form.input(hiddenAction("redeploy"));

                    Static deployLabel = text(isCurrent ? "Redeploy" : "Deploy");
                    return compound( //
                            form, //
                            buttonGroup().button( //
                                    button().size(XS).style(primary).forForm(id).body(deployLabel)) //
                    );
                }
            };

    private static final Component DEPLOYMENT_INFO = new Component() {
        @Override
        public void writeTo(BuildContext out) {
            Deployment deployment = out.get(Deployment.class);
            DescriptionListBuilder description = descriptionList().horizontal();

            description.title("Name").description(text(deployment.getName())).build();
            description.nl();
            description.title("Context-Root").description(text(deployment.getContextRoot())).build();
            description.nl();
            description.title("Version").description(textOr(deployment.getVersion(), "unknown")).build();
            description.nl();
            description.title("CheckSum").description(text(deployment.getCheckSum())).build();

            description.build().writeTo(out);
        }
    };

    private static final String MAIN_FORM_ID = "main";

    private static final AppendingComponent<URI> DEPLOYMENT_LINK = new AppendingComponent<URI>() {
        @Override
        protected URI contentFrom(BuildContext out) {
            return Deployments.path(out.get(UriInfo.class), out.get(Deployment.class).getContextRoot());
        }
    };

    private static Component UNDEPLOY = div().attr("style", "float: right").body(compound( //
            form("undeploy").action(DEPLOYMENT_LINK) //
                    .input(hiddenInput().name("contextRoot").value(new AppendingComponent<ContextRoot>() {
                        @Override
                        protected ContextRoot contentFrom(BuildContext out) {
                            return out.get(Deployment.class).getContextRoot();
                        }
                    })) //
                    .input(hiddenInput().name("checksum").value(new AppendingComponent<CheckSum>() {
                        @Override
                        protected CheckSum contentFrom(BuildContext out) {
                            return out.get(Deployment.class).getCheckSum();
                        }
                    })) //
                    .input(hiddenAction("undeploy")) //
            , //
            buttonGroup().button( //
                    button().size(S).style(danger).forForm("undeploy").body(text("Undeploy")) //
                    ) //
            )).build();

    private static final DeployerPage EXISTING_DEPLOYMENT_FORM = page() //
            .panelBody(compound("\n", UNDEPLOY, DEPLOYMENT_INFO)) //
            .body(nl()) //
            .body(AVAILABLE_VERSIONS) //
            .build();

    private static final DeployerPage NEW_DEPLOYMENT_FORM = page().panelBody(compound( //
            p("Enter the checksum of a new artifact to deploy"), //
            form(MAIN_FORM_ID) //
                    .action(new AppendingComponent<URI>() {
                        @Override
                        protected URI contentFrom(BuildContext out) {
                            return Deployments.base(out.get(UriInfo.class));
                        }
                    }) //
                    .body(hiddenAction("deploy")) //
                    .body(input("checksum").placeholder("Checksum").required().autofocus()) //
                    .body(input("name").placeholder("Deployment Name (optional)")), //
            buttonGroup() //
                    .button(button().style(primary).forForm(MAIN_FORM_ID).body(text("Deploy"))) //
            )).build();

    @Override
    protected void prepare(BuildContext buildContext) {
        buildContext.put(Navigation.DEPLOYMENTS);
    }

    @Override
    protected Component component() {
        return new Component() {
            @Override
            public void writeTo(BuildContext out) {
                Deployment target = out.get(Deployment.class);
                Component page;
                if (target.isNew())
                    page = NEW_DEPLOYMENT_FORM;
                else
                    page = EXISTING_DEPLOYMENT_FORM;
                page.writeTo(out);
            }
        };
    }
}
