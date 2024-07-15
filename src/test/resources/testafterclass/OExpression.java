/* Generated By:JJTree: Do not edit this line. OExpression.java Version 4.3 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=true,NODE_PREFIX=O,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package com.orientechnologies.orient.core.sql.parser;

import com.orientechnologies.orient.core.command.OCommandContext;
import com.orientechnologies.orient.core.db.record.OIdentifiable;
import com.orientechnologies.orient.core.id.ORecordId;

import java.util.Map;

public class OExpression extends SimpleNode {

  protected Boolean singleQuotes;
  protected Boolean doubleQuotes;

  public OExpression(int id) {
    super(id);
  }

  public OExpression(OrientSql p, int id) {
    super(p, id);
  }

  /** Accept the visitor. **/
  public Object jjtAccept(OrientSqlVisitor visitor, Object data) {
    return visitor.visit(this, data);
  }

  public Object execute(OIdentifiable iCurrentRecord, OCommandContext ctx) {
    if (value instanceof ORid) {
      ORid v = (ORid) value;
      return new ORecordId(v.cluster.getValue().intValue(), v.position.getValue().longValue());
    } else if (value instanceof OInputParameter) {
      return null;// TODO
    } else if (value instanceof OMathExpression) {
      return ((OMathExpression) value).execute(iCurrentRecord, ctx);
    } else if (value instanceof OJson) {
      return null;// TODO
    } else if (value instanceof String) {
      return value;
    } else if (value instanceof Number) {
      return value;
    }

    return value;

  }

  public String getDefaultAlias() {

    if (value instanceof String) {
      return (String) value;
    }
    // TODO create an interface for this;

    // if (value instanceof ORid) {
    // return null;// TODO
    // } else if (value instanceof OInputParameter) {
    // return null;// TODO
    // } else if (value instanceof OMathExpression) {
    // return null;// TODO
    // } else if (value instanceof OJson) {
    // return null;// TODO
    // }

    return "" + value;

  }

  @Override
  public String toString() {
    if (value == null) {
      return "null";
    } else if (value instanceof SimpleNode) {
      return value.toString();
    } else if (value instanceof String) {
      if (Boolean.TRUE.equals(singleQuotes)) {
        return "'" + value + "'";
      }
      return "\"" + value + "\"";
    } else {
      return "" + value;
    }
  }

  public static String encode(String s) {
    return s.replaceAll("\"", "\\\\\"");
  }

  public void replaceParameters(Map<Object, Object> params) {
    if (value instanceof OInputParameter) {
      value = ((OInputParameter) value).bindFromInputParams(params);
    } else if (value instanceof OBaseExpression) {
      ((OBaseExpression) value).replaceParameters(params);
    }
  }
}
/* JavaCC - OriginalChecksum=9c860224b121acdc89522ae97010be01 (do not edit this line) */
