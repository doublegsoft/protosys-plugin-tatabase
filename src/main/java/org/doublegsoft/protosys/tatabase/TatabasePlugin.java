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

    options.addOption("m", "model", true, "模型定义文件");
    options.addOption("d", "dependent-model", true, "依赖模型定义文件");
    options.addOption("t", "template-root", true, "模板定义根目录");
    options.addOption("o", "output-root", true, "输出跟路径");
    options.addOption("b", "tatabase", true, "tatabase数据目录");
    options.addOption("l", "license", true, "license数据文件");
    options.addOption("g", "globals", true, "全局常量");
    options.addOption("a", "apifiles", true, "API数据目录");

    CommandLineParser parser = new DefaultParser();
    CommandLine cmd = parser.parse(options, args);

    String modelPath = cmd.getOptionValue("model");
    String dependentModelPath = cmd.getOptionValue("dependent-model");
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
    if (dependentModelPath != null) {
      modelPath += ";" + dependentModelPath;
    }
    ModelDefinition model = tatabase.createModelFromModelbase(modelPath.split(";"));

    ApplicationDefinition app = new ApplicationDefinition();

    String applicationName = globalVars.get("application");
    String databaseName = globalVars.get("database");
    if (applicationName == null) {
      applicationName = globalVars.get("application");
    }
    app.setName(applicationName);
    app.setModel(model);

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

    for (ObjectDefinition obj : model.getObjects()) {
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
      tatabase.prototype(app, model, outputRoot, templateRoot, globalVars);
    } catch (Throwable cause) {
      cause.printStackTrace();
    }

  }
}
