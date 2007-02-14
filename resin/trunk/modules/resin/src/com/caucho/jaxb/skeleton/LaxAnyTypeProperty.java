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
 *
 *   Free Software Foundation, Inc.
 *   59 Temple Place, Suite 330
 *   Boston, MA 02111-1307  USA
 *
 * @author Adam Megacz
 */

package com.caucho.jaxb.skeleton;

import com.caucho.jaxb.BinderImpl;
import com.caucho.jaxb.JAXBContextImpl;
import com.caucho.jaxb.JAXBUtil;
import com.caucho.util.L10N;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.ValidationEventHandler;
import javax.xml.namespace.QName;
import javax.xml.stream.events.*;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;

import org.w3c.dom.Node;

/**
 * a property referencing some other Skeleton
 */
public class LaxAnyTypeProperty extends Property {
  private static final L10N L = new L10N(LaxAnyTypeProperty.class);

  private LaxAnyTypeSkeleton _skeleton;

  public LaxAnyTypeProperty(JAXBContextImpl context)
    throws JAXBException
  {
    _skeleton = new LaxAnyTypeSkeleton(context);
  }

  public Object read(Unmarshaller u, XMLStreamReader in, Object previous)
    throws IOException, XMLStreamException, JAXBException
  {
    Object obj = _skeleton.read(u, in);

    // essentially a nextTag() that handles end of document gracefully
    while (in.hasNext()) {
      in.next();

      if (in.getEventType() == in.START_ELEMENT ||
          in.getEventType() == in.END_ELEMENT)
        break;
    }

    return obj;
}
  
  public Object read(Unmarshaller u, XMLEventReader in, Object previous)
    throws IOException, XMLStreamException, JAXBException
  {
    Object ret = _skeleton.read(u, in);

    while (in.hasNext()) {
      XMLEvent event = in.peek();

      if (event.isEndElement() ||
          event.isStartElement())
        break;

      in.nextEvent();
    }

    return ret;
  }

  public Object bindFrom(BinderImpl binder, NodeIterator node, Object previous)
    throws JAXBException
  {
    return _skeleton.bindFrom(binder, null, node);
  }

  public void write(Marshaller m, XMLStreamWriter out, Object obj, QName qname)
    throws IOException, XMLStreamException, JAXBException
  {
    _skeleton.write(m, out, obj, qname);
  }

  public void write(Marshaller m, XMLEventWriter out, Object obj, QName qname)
    throws IOException, XMLStreamException, JAXBException
  {
    _skeleton.write(m, out, obj, qname);
  }

  public Node bindTo(BinderImpl binder, Node node, Object obj, QName qname)
    throws JAXBException
  {
    return _skeleton.bindTo(binder, node, obj, qname);
  }

  public String getSchemaType()
  {
    return "xsd:anyType";
  }

  public boolean isXmlPrimitiveType()
  {
    return false;
  }

  public String toString()
  {
    return "LaxAnyTypeProperty[" + _skeleton + "]";
  }
}


