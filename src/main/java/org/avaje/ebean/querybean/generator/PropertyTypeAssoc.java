package org.avaje.ebean.querybean.generator;

import java.io.IOException;
import java.io.Writer;
import java.util.Set;

/**
 * Property type for associated beans (OneToMany, ManyToOne etc).
 */
public class PropertyTypeAssoc extends PropertyType {

  /**
   * The package name for this associated query bean.
   */
  private final String assocPackage;

  /**
   * Construct given the associated bean type name and package.
   *
   * @param qAssocTypeName the associated bean type name.
   * @param assocPackage   the associated bean package.
   */
  public PropertyTypeAssoc(String qAssocTypeName, String assocPackage) {
    super(qAssocTypeName);
    this.assocPackage = assocPackage;
  }

  /**
   * Returns true as associated bean type.
   */
  @Override
  public boolean isAssociation() {
    return true;
  }

  /**
   * All required imports to the allImports set.
   */
  @Override
  public void addImports(Set<String> allImports) {
    allImports.add(assocPackage + "." + propertyType);
  }

  /**
   * Write the constructor source code to writer.
   */
  @Override
  public void writeConstructor(Writer writer, String name, boolean assoc) throws IOException {

    writer.append(propertyType).append("<>(\"").append(name).append("\"");
    if (assoc) {
      //this.notes = new QAssocContactNote<>("notes", root, path, depth);
      writer.append(", root, path, depth);").append(NEWLINE);

    } else {
      // root level
      writer.append(", this, ").append("maxDepth);").append(NEWLINE);
    }
  }
}
