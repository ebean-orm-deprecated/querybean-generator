package io.ebean.querybean.generator;

import java.io.IOException;
import java.io.Writer;

/**
 * Meta data for a property.
 */
public class PropertyMeta {

  /**
   * The property name.
   */
  private final String name;

  /**
   * The property type.
   */
  private final PropertyType type;

  /**
   * Construct given the property name and type.
   */
  public PropertyMeta(String name, PropertyType type) {
    this.name = name;
    this.type = type;
  }

  /**
   * Return true if this is an associated bean property (OneToMany, ManyToOne etc).
   */
  public boolean isAssociation() {
    return type.isAssociation();
  }


  /**
   * Return the type definition given the type short name and flag indicating if it is an associated bean type.
   */
  public String getTypeDefn(String shortName, boolean assoc) {
    return type.getTypeDefn(shortName, assoc);
  }

  public void writeFieldDefn(Writer writer, String shortName, boolean assoc) throws IOException {

    writer.append("  public ");
    writer.append(getTypeDefn(shortName, assoc));
    writer.append(" ").append(name).append(";");
  }

  public void writeConstructorSimple(Writer writer, String shortName, boolean assoc) throws IOException {

    if (!type.isAssociation()) {
      writer.append("    this.").append(name).append(" = new ");
      type.writeConstructor(writer, name, assoc);
    }
  }

  public void writeConstructorAssoc(Writer writer, String shortName, boolean assoc) throws IOException {
    if (type.isAssociation()) {
      if (assoc) {
        writer.append("  ");
      }
      writer.append("    this.").append(name).append(" = new ");
      type.writeConstructor(writer, name, assoc);
    }
  }

  public void writeFieldAliasDefn(Writer writer, String shortName) throws IOException {

    writer.append("    public static ");
    writer.append(getTypeDefn(shortName, false));
    writer.append(" ").append(name).append(" = _alias.").append(name).append(";");
  }
}
