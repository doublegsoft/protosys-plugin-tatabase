package org.doublegsoft.protosys.tatabase;

import com.doublegsoft.jcommons.lang.HashObject;
import com.doublegsoft.jcommons.lang.StringHolder;
import com.doublegsoft.jcommons.lang.StringPair;
import com.doublegsoft.jcommons.metabean.AttributeDefinition;
import com.doublegsoft.jcommons.metabean.ModelDefinition;
import com.doublegsoft.jcommons.metabean.ObjectDefinition;
import com.doublegsoft.jcommons.metamodel.ApplicationDefinition;
import com.doublegsoft.jcommons.metamodel.UsecaseDefinition;
import com.doublegsoft.jcommons.metaui.PageDefinition;
import com.doublegsoft.jcommons.metaui.WidgetDefinition;
import com.doublegsoft.jcommons.metaui.layout.Position;
import com.doublegsoft.jcommons.programming.c.CConventions;
import com.doublegsoft.jcommons.programming.go.GoConventions;
import com.doublegsoft.jcommons.programming.objc.ObjcConventions;
import com.doublegsoft.jcommons.programming.rust.RustConventions;
import com.doublegsoft.jcommons.utils.Inflector;
import com.doublegsoft.jcommons.utils.Strings;
import com.google.gson.Gson;
import freemarker.cache.FileTemplateLoader;
import freemarker.cache.MultiTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.template.DefaultObjectWrapper;
import io.doublegsoft.guidbase.GuidbaseContext;
import io.doublegsoft.guidbase.GuidbaseWidget;
import io.doublegsoft.modelbase.Modelbase;
import io.doublegsoft.tatabase.Format;
import io.doublegsoft.tatabase.Tatabase;
import io.doublegsoft.tatabase.TatabaseBuilder;
import io.doublegsoft.typebase.EnumValue;
import io.doublegsoft.typebase.Typebase;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.doublegsoft.protosys.commons.FileSystemTemplateBasedPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;

/**
 * Uses modelbase dsl language approach to generate test data.
 *
 * @author <a href="mailto:guo.guo.gan@gmail.com">Christian Gann</a>
 *
 * @since 1.0
 */
public class TatabasePlugin extends FileSystemTemplateBasedPlugin {

  public void prototype(ApplicationDefinition app, ModelDefinition model, String outputRoot, String templateRoot, HashObject globals) throws IOException {
    FileTemplateLoader specific = new FileTemplateLoader(new File(templateRoot));
    // FileTemplateLoader specificForTest = new FileTemplateLoader(new File("/Volumes/EXPORT/local/works/doublegsoft.io/modelbase/03.Development/modelbase-data"));
    FileTemplateLoader common = new FileTemplateLoader(new File(templateRoot + "/.."));
    FileTemplateLoader common2 = new FileTemplateLoader(new File(templateRoot + "/../.."));
    MultiTemplateLoader templateLoader = new MultiTemplateLoader(new TemplateLoader[]{common, common2, specific/*, specificForTest*/});
    FREEMARKER.setTemplateLoader(templateLoader);
    FREEMARKER.setSharedVariable("statics", ((DefaultObjectWrapper) FREEMARKER.getObjectWrapper()).getStaticModels());

    decorate(model, globals);
    decorate(app, globals);

    if (globals != null) {
      globalVariables.putAll(globals);
    }
    globalVariables.put("objectConstructor", new freemarker.template.utility.ObjectConstructor());
    visitAndRender(outputRoot, "", templateRoot, "", app, new HashObject());
  }

  public static void main(String[] args) throws Exception {
    Options options = new Options();

    options.addOption("m", "modelbase-model", true, "Modelbase模型定义文件");
    options.addOption("g", "guidbase-model", true, "Guidbase模型定义文件");
    options.addOption("t", "template-root", true, "模板定义根目录");
    options.addOption("o", "output-root", true, "输出跟路径");
    options.addOption("l", "license", true, "license数据文件");
    options.addOption("g", "globals", true, "全局常量");

    CommandLineParser parser = new DefaultParser();
    CommandLine cmd = parser.parse(options, args);

    String modelbasePath = cmd.getOptionValue("modelbase-model");
    String guidbasePath = cmd.getOptionValue("guidbase-model");
    String templateRoot = cmd.getOptionValue("template-root");
    String outputRoot = cmd.getOptionValue("output-root");
    String licensePath = cmd.getOptionValue("license");
    String globals = cmd.getOptionValue("globals");

    // globals
    HashObject globalVars = new HashObject();
    Gson gson = new Gson();
    if (globals != null) {
      globalVars.putAll(gson.fromJson(globals, Map.class));
    }

    // license
    String license = null;
    if (licensePath != null) {
      license = new String(Files.readAllBytes(new File(licensePath).toPath()), "UTF-8");
      globalVars.set("license", license);
    }

    globalVars.set("typebase", new Typebase());
    globalVars.set("tatabase", new Tatabase());
    globalVars.set("inflector", new Inflector());

    TatabasePlugin tatabase = new TatabasePlugin();
    if (guidbasePath != null) {
      modelbasePath += ";" + guidbasePath;
    }
    ApplicationDefinition app = new ApplicationDefinition();
    String applicationName = globalVars.get("application");
    String databaseName = globalVars.get("database");
    if (applicationName == null) {
      applicationName = globalVars.get("application");
    }
    app.setName(applicationName);

    ModelDefinition dataModel = tatabase.createModelFromModelbase(modelbasePath.split(";"));
    app.setModel(dataModel);
    if (!Strings.isEmpty(guidbasePath)) {
      String[] paths = guidbasePath.split(";");
      StringBuilder guidbaseModel = new StringBuilder();
      for (String guidbaseFile : paths) {
        guidbaseModel.append(new String(Files.readAllBytes(new File(guidbaseFile).toPath()))).append("\n");
      }
      List<PageDefinition> pages = createPages(guidbaseModel.toString(), tatabase);
      pages.forEach(app::addPage);
    }

    String namingClass = globalVars.get("naming");
    if (namingClass != null) {
      Object naming = Class.forName(namingClass).newInstance();
      globalVars.set("naming", naming);
    }

    namingClass = globalVars.get("globalNamingConvention");
    if (namingClass != null) {
      Object naming = Class.forName(namingClass).newInstance();
      globalVars.set("globalNamingConvention", naming);
    }

    for (ObjectDefinition obj : dataModel.getObjects()) {
      if (obj.isLabelled("generated")) {
        continue;
      }
      if (!Strings.isEmpty(databaseName)) {
        if (obj.isLabelled("persistence")) {
          obj.getLabelledOptions("persistence").put("namespace", databaseName);
        }
      }
      Map<String, String> moduleOpts = new HashMap<>();
      moduleOpts.putAll(obj.getLabelledOptions("module"));
      if (obj.getModuleName() == null) {
        obj.setModuleName(applicationName);
        moduleOpts.put("name", applicationName);
      }
      obj.setLabelledOptions("module", moduleOpts);
    }

    try {
      tatabase.prototype(app, dataModel, outputRoot, templateRoot, globalVars);
    } catch (Throwable cause) {
      cause.printStackTrace();
    }
  }

  public static List<PageDefinition> createPages(String guidbaseSource, TatabasePlugin tatabase) throws IOException {
    List<PageDefinition> retVal = new ArrayList<>();
    List<GuidbaseContext> guicctxs = GuidbaseContext.from(guidbaseSource);

    for (GuidbaseContext guicctx : guicctxs) {
      String pageId = guicctx.page().id();
      String module = guicctx.page().attr("module");
      if (module == null) {
        if (pageId.contains("/")) {
          module = pageId.substring(0, pageId.lastIndexOf("/"));
        } else {
          module = "unknown";
        }
      }

      PageDefinition pageDef = new PageDefinition(module);
      if (pageId.contains("/")) {
        pageDef.setName(pageId.substring(pageId.lastIndexOf("/") + 1));
      } else {
        pageDef.setName(pageId);
      }
      pageDef.setType("page");
      pageDef.setModule(module);
      pageDef.setId(guicctx.page().id());
      pageDef.setTitle(guicctx.page().attr("title"));
      pageDef.setPosition(Position.at(guicctx.page().attr("position")));
      guicctx.page().attrs().forEach(attr -> {
        pageDef.addOption(attr.name(), attr.value());
      });
      for (GuidbaseWidget widget : guicctx.page().children()) {
        pageDef.addWidget(tatabase.convertToWidget(widget, pageDef));
      }
      retVal.add(pageDef);
    }
    return retVal;
  }
}
