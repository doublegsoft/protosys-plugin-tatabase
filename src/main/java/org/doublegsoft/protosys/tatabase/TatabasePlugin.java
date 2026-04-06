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
import io.doublegsoft.tatabase.Format;
import io.doublegsoft.tatabase.TatabaseBuilder;
import org.doublegsoft.protosys.commons.FileSystemTemplateBasedPlugin;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * Uses modelbase dsl language approach to generate test data.
 *
 * @author <a href="mailto:guo.guo.gan@gmail.com">Christian Gann</a>
 *
 * @since 1.0
 */
public class TatabasePlugin extends FileSystemTemplateBasedPlugin {

  /**
   * Generates insert-sql statements as sql format for the data model in application.
   *
   * @param model
   *          the application data model
   *
   * @param globals
   *          the data and generated data holder
   *
   * @throws IOException
   *          in case of io errors
   */
  @Override
  public void decorate(ModelDefinition model, HashObject globals) throws IOException {
    if (model == null || model.getObjects().length == 0) {
      return;
    }
    // tatabase dsl for data model
    StringHolder ttb = new StringHolder();
    int size = 100;
    for (ObjectDefinition obj : model.getObjects()) {
      if (obj.isLabelled("event")) {
        continue;
      } else if (obj.isLabelled("value")) {
        continue;
      }
      String table = obj.getPersistenceName().toLowerCase();
      ttb.append(table).append("[" + size + "]<").linefeed();
      int index = 0;
      for (AttributeDefinition attr : obj.getAttributes()) {
        String expr = "null";
        String domainType = String.valueOf(attr.getConstraint().getDomainType());
        if ("uuid".equalsIgnoreCase(domainType)) {
          expr = "'" + attr.getParent().getName().toUpperCase() + "'" + " + [1, " + size + "]";
        } else if ("lmt".equalsIgnoreCase(domainType)) {
          expr = "lmt";
        } else if (attr.getDirectRelationship() != null && attr.getConstraint().getDomainType() != null) {
          ObjectDefinition directObject = attr.getDirectRelationship().getDirectTarget();
          if (directObject.getIdentifiableAttribute() != null) {
            expr = "&" + directObject.getPersistenceName().toLowerCase() + "(" +
                directObject.getIdentifiableAttribute().getPersistenceName().toLowerCase() + ")";
          } else {
            expr = "null";
          }
        } else if (attr.isIdentifiable() && ("null".equalsIgnoreCase(domainType) || domainType.isEmpty())) {
          // if no domain type and is id, specify it range code
          expr = "'" + attr.getName() + "'" + " + [1, " + size + "]";
        } else if (domainType.toLowerCase().startsWith("enum")
            || domainType.toLowerCase().startsWith("state")) {
          String enumexpr = domainType.replace("ENUM", "enum").replace("state", "enum");
          List<StringPair> pairs = TYPEBASE.enumtype(enumexpr);
          StringHolder vals = new StringHolder();
          vals.append("[");
          pairs.forEach(pair -> {
            vals.append(pair.getKey()).append(", ");
          });
          // remove the last comma and space
          expr = vals.toString().substring(0, vals.toString().length() - 2) + "]";
        } else if (domainType.toLowerCase().startsWith("string")) {
          expr = "'" + attr.getPersistenceName().toUpperCase() + "' + [1, 100]";
        } else if (domainType.indexOf("#") != -1) {
          // domain type: organization#name
          expr = domainType.toLowerCase();
        } else {
          // domain type: name
          expr = "null";
        }
        if (attr.getPersistenceName() != null) {
          ttb.indent(4).append(attr.getPersistenceName().toLowerCase()).append(": ").append(expr);
        }
        if (index != obj.getAttributes().length - 1) {
          ttb.append(",");
        }
        ttb.linefeed();
        index++;
      }
      ttb.append(">").linefeed().linefeed();
    }

    // tatabase sqls
    try {
      Collection<?> testsqls = new TatabaseBuilder().parse(ttb.toString()).build(Format.SQL).values();
      globals.set("testsqls", testsqls);
    } catch (Exception ex) {
      ex.printStackTrace(System.err);
    }
    globals.set("ttb", ttb.toString());
  }

  /**
   * Generates mock data as json format for usecases in application.
   *
   * @param application
   *          the application definition
   *
   * @param globals
   *          the data and generated data holder
   *
   * @throws IOException
   *          in case of io errors
   */
  @Override
  public void decorate(ApplicationDefinition application, HashObject globals) throws IOException {
    for (UsecaseDefinition usecase : application.getUsecases()) {
//      PageDefinition page = usecase.getPage();
//      for (WidgetDefinition widget : page.getPageWidgets()) {
//        String id = widget.getId();
//        String type = widget.getType();
//        if ("listview".equalsIgnoreCase(type)) {
//          StringHolder tatabaseDsl = new StringHolder();
//          tatabaseDsl.append(id).append("[20]<").linefeed();
//          int index = 0;
//          int size = widget.getWidgets().size();
//          for (WidgetDefinition child : widget.getWidgets()) {
//            tatabaseDsl.indent(4).append(child.getId()).append(": ").append(widget2DomainType(child));
//            if (index != size - 1) {
//              tatabaseDsl.append(",");
//            }
//            tatabaseDsl.linefeed();
//            index++;
//          }
//          tatabaseDsl.append(">").linefeed().linefeed();
//          HashObject mock = new HashObject();
//          mock.set("mock", id);
//          mock.set("json", new TatabaseBuilder().parse(tatabaseDsl.toString()).build(Format.JSON).values().iterator().next());
//          mock.set("ttb", tatabaseDsl.toString());
//          globals.add("mocks", mock);
//        } else if ("form".equalsIgnoreCase(type)) {
//          // TODO
//          HashObject form = new HashObject();
//          for (WidgetDefinition child : widget.getWidgets()) {
//
//          }
//        }
//      }
    }
  }

  private String widget2DomainType(WidgetDefinition widget) {
    // TODO: ENRICH
    if ("image".equalsIgnoreCase(widget.getType())) {
      return "'http://via.placeholder.com/240x160'";
    }
    return "name";
  }
}
