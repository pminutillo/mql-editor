package org.pentaho.commons.metadata.mqleditor.editor.models;

import java.util.List;

import org.pentaho.commons.metadata.mqleditor.MqlDomain;
import org.pentaho.commons.metadata.mqleditor.beans.Domain;
import org.pentaho.commons.metadata.mqleditor.beans.Model;

public class UIDomain extends AbstractModelNode<UIModel> implements MqlDomain<UIModel> {
  
  private String id, name;
  
  public UIDomain(){
  }
  
  public UIDomain(Domain domain){
    this.id = domain.getId();
    this.name = domain.getName();
    
    for(Model model : domain.getModels()){
      this.children.add(new UIModel(model));
    }
  }

  public String getId() {
    return id;
  }
  
  public void setId(String id){
    this.id = id;
  }

  public String getName() {
    return this.name;
  }
  
  public void setName(String name){
    this.name = name;
  }
  
  public List<UIModel> getModels() {
    return this.getChildren();
  }

  public void setModels(List<UIModel> models) {
    this.children = models;
  }
  
}

  