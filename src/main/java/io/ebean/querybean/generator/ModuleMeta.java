package io.ebean.querybean.generator;

import java.util.List;

class ModuleMeta {
  private final List<String> entities;
  private final List<String> other;

  public ModuleMeta(List<String> entities, List<String> other) {
    this.entities = entities;
    this.other = other;
  }

  public List<String> getEntities() {
    return entities;
  }

  public List<String> getOther() {
    return other;
  }
}
