package io.ebean.querybean.generator;

interface Constants {

  String AT_GENERATED = "@Generated(\"io.ebean.querybean.generator\")";

  String AT_TYPEQUERYBEAN = "@TypeQueryBean(\"v1\")";

  String GENERATED_9 = "javax.annotation.processing.Generated";
  String GENERATED_8 = "javax.annotation.Generated";

  String MAPPED_SUPERCLASS = "javax.persistence.MappedSuperclass";
  String INHERITANCE = "javax.persistence.Inheritance";
  String ENTITY = "javax.persistence.Entity";
  String EMBEDDABLE = "javax.persistence.Embeddable";

  String DBARRAY = "io.ebean.annotation.DbArray";
  String DBJSON = "io.ebean.annotation.DbJson";
  String DBJSONB = "io.ebean.annotation.DbJsonB";
  String DBNAME = "io.ebean.annotation.DbName";

  String TQROOTBEAN = "io.ebean.typequery.TQRootBean";
  String TQASSOCBEAN = "io.ebean.typequery.TQAssocBean";
  String TQPROPERTY = "io.ebean.typequery.TQProperty";
  String TYPEQUERYBEAN = "io.ebean.typequery.TypeQueryBean";
  String DATABASE = "io.ebean.Database";
  String DB = "io.ebean.DB";

}
