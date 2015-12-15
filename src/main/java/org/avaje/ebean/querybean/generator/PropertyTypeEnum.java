package org.avaje.ebean.querybean.generator;


import java.util.Set;

/**
 * Enum property type.
 */
public class PropertyTypeEnum extends PropertyType {

  private final String enumClass;

  private final String enumShortName;

  public PropertyTypeEnum(String enumClass, String enumShortName) {
    super("PEnum");
    this.enumClass = enumClass;
    this.enumShortName = enumShortName;
  }

  @Override
  public String getTypeDefn(String shortName, boolean assoc) {
    if (assoc) {
      return "PEnum<R," + enumShortName + ">";

    } else {
      return "PEnum<Q" + shortName + "," + enumShortName + ">";
    }
  }

  @Override
  public void addImports(Set<String> allImports) {
    super.addImports(allImports);
    allImports.add(enumClass);
  }

}
