/*
 * Copyright (c) 1998-2006 Caucho Technology -- all rights reserved
 *
 * This file is part of Resin(R) Open Source
 *
 * Each copy or derived work must preserve the copyright notice and this
 * notice unmodified.
 *
 * Resin Open Source is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * Resin Open Source is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE, or any warranty
 * of NON-INFRINGEMENT.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Resin Open Source; if not, write to the
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Scott Ferguson
 */

package com.caucho.amber.field;

import java.util.logging.Logger;
import javax.persistence.CascadeType;
import com.caucho.util.L10N;
import com.caucho.log.Log;
import com.caucho.config.ConfigException;
import com.caucho.amber.type.RelatedType;
import com.caucho.amber.table.LinkColumns;


/**
 * Configuration for a bean's field
 */
public class AssociationField extends CollectionField {
  private static final L10N L = new L10N(AssociationField.class);
  protected static final Logger log = Log.open(AssociationField.class);

  private LinkColumns _linkColumns;

  private boolean _hasJoinColumns;

  private boolean _hasInverseJoinColumns;

  public AssociationField(RelatedType relatedType,
                          String name,
                          CascadeType[] cascadeTypes)
    throws ConfigException
  {
    super(relatedType, name, cascadeTypes);
  }

  public AssociationField(RelatedType relatedType)
  {
    super(relatedType);
  }

  /**
   * Returns true if this field is annotated with
   * @JoinTable and the attribute joinColumns.
   */
  public boolean hasJoinColumns()
  {
    return _hasJoinColumns;
  }

  /**
   * Sets true if this field is annotated with
   * @JoinTable and the attribute joinColumns.
   */
  public void setJoinColumns(boolean hasJoinColumns)
  {
    _hasJoinColumns = hasJoinColumns;
  }

  /**
   * Returns true if this field is annotated with
   * @JoinTable and the attribute inverseJoinColumns.
   */
  public boolean hasInverseJoinColumns()
  {
    return _hasInverseJoinColumns;
  }

  /**
   * Sets true if this field is annotated with
   * @JoinTable and the attribute inverseJoinColumns.
   */
  public void setInverseJoinColumns(boolean hasInverseJoinColumns)
  {
    _hasInverseJoinColumns = hasInverseJoinColumns;
  }
  /**
   * Sets the result columns.
   */
  public void setColumn(LinkColumns columns)
  {
    _linkColumns = columns;
  }

  /**
   * Gets the result.
   */
  public LinkColumns getColumn()
  {
    return _linkColumns;
  }

  /**
   * Generates the target select.
   */
  public String generateTargetSelect(String id)
  {
    return getColumn().generateSelectSQL(id);
  }
}
