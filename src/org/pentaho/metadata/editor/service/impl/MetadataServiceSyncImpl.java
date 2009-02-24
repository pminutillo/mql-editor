package org.pentaho.metadata.editor.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.pentaho.commons.mql.ui.mqldesigner.MQLWhereConditionModel;
import org.pentaho.metadata.ColumnType;
import org.pentaho.metadata.IBusinessColumn;
import org.pentaho.metadata.ICondition;
import org.pentaho.metadata.IDomain;
import org.pentaho.metadata.IModel;
import org.pentaho.metadata.IOrder;
import org.pentaho.metadata.IQuery;
import org.pentaho.metadata.beans.BusinessColumn;
import org.pentaho.metadata.beans.BusinessTable;
import org.pentaho.metadata.beans.Category;
import org.pentaho.metadata.beans.Domain;
import org.pentaho.metadata.beans.Model;
import org.pentaho.metadata.utils.ModelSerializer;
import org.pentaho.pms.core.CWM;
import org.pentaho.pms.factory.CwmSchemaFactory;
import org.pentaho.pms.factory.CwmSchemaFactoryInterface;
import org.pentaho.pms.mql.MQLQuery;
import org.pentaho.pms.mql.MQLQueryImpl;
import org.pentaho.pms.mql.OrderBy;
import org.pentaho.pms.mql.Selection;
import org.pentaho.pms.mql.WhereCondition;
import org.pentaho.pms.schema.BusinessCategory;
import org.pentaho.pms.schema.BusinessModel;
import org.pentaho.pms.schema.SchemaMeta;
import org.pentaho.pms.schema.concept.types.datatype.DataTypeSettings;
import org.pentaho.pms.util.UniqueList;

public class MetadataServiceSyncImpl {

  private String locale = Locale.getDefault().toString();

  private List<IDomain> domains = new ArrayList<IDomain>();

  private CwmSchemaFactoryInterface factory;

  /**
   * Keeps track of where a particular model came from.
   */
  private Map<String, SchemaMeta> modelIdToSchemaMetaMap = new HashMap<String, SchemaMeta>();

  public MetadataServiceSyncImpl(List<CWM> cwms, CwmSchemaFactoryInterface factory) {
    this.factory = factory;

    for (CWM cwm : cwms) {
      SchemaMeta meta = factory.getSchemaMeta(cwm);
      Domain domain = new Domain();
      domain.setName(meta.getDomainName());
      UniqueList<BusinessModel> models = meta.getBusinessModels();
      for (BusinessModel model : models) {
        Model myModel = createModel(model);
        domain.getModels().add(myModel);
        modelIdToSchemaMetaMap.put(myModel.getId(), meta);
      }
      domains.add(domain);
    }
  }

  private Model createModel(BusinessModel m) {
    Model model = new Model();
    model.setName(m.getName(locale));
    model.setId(m.getId());

    UniqueList<BusinessCategory> cats = m.getRootCategory().getBusinessCategories();
    for (BusinessCategory cat : cats) {
      model.getCategories().add(createCategory(cat));
    }

    return model;
  }

  private Category createCategory(BusinessCategory c) {
    Category cat = new Category();
    cat.setName(c.getName(locale));
    cat.setId(c.getId());
    UniqueList<org.pentaho.pms.schema.BusinessColumn> cols = c.getBusinessColumns();
    for (org.pentaho.pms.schema.BusinessColumn col : cols) {
      cat.getBusinessColumns().add(createColumn(col));
    }

    return cat;
  }

  private BusinessColumn createColumn(org.pentaho.pms.schema.BusinessColumn c) {
    BusinessColumn col = new BusinessColumn();
    col.setName(c.getName(locale));
    col.setId(c.getId());
    col.setTable(createTable(c.getBusinessTable()));

    int type = c.getPhysicalColumn().getDataType().getType();
    ColumnType ourType = null;
    switch (type) {
      case DataTypeSettings.DATA_TYPE_BOOLEAN:
        ourType = ColumnType.BOOLEAN;
        break;
      case DataTypeSettings.DATA_TYPE_STRING:
        ourType = ColumnType.TEXT;
        break;
      case DataTypeSettings.DATA_TYPE_NUMERIC:
        ourType = ColumnType.FLOAT;
        break;
      case DataTypeSettings.DATA_TYPE_DATE:
        ourType = ColumnType.DATE;
        break;
    }

    col.setType(ourType);
    return col;
  }

  private BusinessTable createTable(org.pentaho.pms.schema.BusinessTable t) {
    BusinessTable table = new BusinessTable();
    table.setName(t.getName(locale));
    table.setId(t.getId());
    return table;
  }

  public List<IDomain> getMetadataDomains() {
    return domains;
  }

  public IDomain getDomainByName(String name) {
    for (IDomain domain : domains) {
      if (domain.getName().equals(name)) {
        return domain;
      }
    }
    return null;
  }

  private org.pentaho.pms.schema.BusinessColumn[] getColumns(BusinessModel model,
      List<? extends IBusinessColumn> thincols) {
    org.pentaho.pms.schema.BusinessColumn[] cols = new org.pentaho.pms.schema.BusinessColumn[thincols.size()];

    int i = 0;
    for (IBusinessColumn thincol : thincols) {
      UniqueList list = model.getAllBusinessColumns();
      for (Object col : list.getList()) {
        if (col.toString().equals(thincol.toString())) {
          cols[i++] = (org.pentaho.pms.schema.BusinessColumn) col;
        }
      }
    }
    return cols;
  }

  private org.pentaho.pms.schema.BusinessColumn getColumn(BusinessModel model, IBusinessColumn thinCol) {
    UniqueList list = model.getAllBusinessColumns();
    for (Object col : list.getList()) {
      if (((org.pentaho.pms.schema.BusinessColumn) col).getName(locale).equals(thinCol.getName())) {
        return (org.pentaho.pms.schema.BusinessColumn) col;
      }
    }
    return null;
  }

  private MQLWhereConditionModel[] getConditions(BusinessModel model, List<? extends ICondition> thinConditions) {
    MQLWhereConditionModel[] conditions = new MQLWhereConditionModel[thinConditions.size()];
    int i = 0;
    for (ICondition thinCondition : thinConditions) {
      org.pentaho.pms.schema.BusinessColumn col = getColumn(model, thinCondition.getColumn());
      MQLWhereConditionModel where = new MQLWhereConditionModel(thinCondition.getCombinationType().toString(), col,
          thinCondition.getCondition("[" + col.toString() + "]"));
      conditions[i++] = where;
    }
    return conditions;
  }

  private List<OrderBy> getOrders(BusinessModel model, List<? extends IOrder> thinOrders) {
    List<OrderBy> ord = new ArrayList<OrderBy>();

    for (IOrder thinOrder : thinOrders) {
      Selection selection = new Selection(getColumn(model, thinOrder.getColumn()));
      ord.add(new OrderBy(selection, (thinOrder.getOrderType() == IOrder.Type.ASC)));
    }
    return ord;
  }

  public String saveQuery(IModel model, List<? extends IBusinessColumn> cols, List<? extends ICondition> conditions,
      List<? extends IOrder> orders) {
    SchemaMeta meta = modelIdToSchemaMetaMap.get(model.getId());

    UniqueList<BusinessModel> models = meta.getBusinessModels();
    BusinessModel realModel = null;
    for (BusinessModel m : models) {
      if (m.getName(locale).equals(model.getName())) {
        realModel = m;
        break;
      }
    }
    if (realModel != null) {

      MQLQuery mqlQuery = null;
      try {

        org.pentaho.pms.schema.BusinessColumn[] businessColumns = getColumns(realModel, cols);
        if (businessColumns.length > 0) {
          BusinessModel businessModel = realModel;
          mqlQuery = new MQLQueryImpl(meta, businessModel, null, meta.getActiveLocale());
          List<Selection> selections = new ArrayList<Selection>();
          for (int i = 0; i < businessColumns.length; i++) {
            selections.add(new Selection(businessColumns[i]));
          }

          mqlQuery.setSelections(selections);
          MQLWhereConditionModel wherelist[] = getConditions(realModel, conditions);
          ArrayList<WhereCondition> constraints = new ArrayList<WhereCondition>();
          BusinessCategory rootCat = businessModel.getRootCategory();
          //mqlQuery.setDisableDistinct(!this.distinctSelections.getSelection());
          for (int i = 0; i < wherelist.length; i++) {
            BusinessCategory businessCategory = rootCat.findBusinessCategoryForBusinessColumn(wherelist[i].getField());

            constraints.add(new WhereCondition(businessModel, wherelist[i].getOperator(), wherelist[i].getCondition()) //$NON-NLS-1$
                );
          }
          mqlQuery.setConstraints(constraints);
          mqlQuery.setOrder(getOrders(realModel, orders));

          return mqlQuery.getXML();
        }
      } catch (Throwable e) { // PMSFormulaException e) {
        e.printStackTrace();
      }

    } else {
      //throw error
    }
    return null;
  }

  public String serializeModel(IQuery query) {
    return ModelSerializer.serialize(query);
  }
}
