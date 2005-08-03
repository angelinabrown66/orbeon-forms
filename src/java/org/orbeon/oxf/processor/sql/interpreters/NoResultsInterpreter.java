/**
 *  Copyright (C) 2004 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.processor.sql.interpreters;

import org.orbeon.oxf.processor.sql.SQLProcessor;
import org.orbeon.oxf.processor.sql.SQLProcessorInterpreterContext;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

/**
 *
 */
public class NoResultsInterpreter extends SQLProcessor.InterpreterContentHandler {

    public NoResultsInterpreter(SQLProcessorInterpreterContext interpreterContext) {
        super(interpreterContext, false);
    }

    public void start(String uri, String localname, String qName, Attributes attributes) throws SAXException {
        // Only forward if the result set is empty
        if (getInterpreterContext().isEmptyResultSet()) {
            setForward(true);
            addElementHandler(new ExecuteInterpreter(getInterpreterContext()), SQLProcessor.SQL_NAMESPACE_URI, "execute");
            ValueOfCopyOfInterpreter valueOfCopyOfInterpreter = new ValueOfCopyOfInterpreter(getInterpreterContext());
            addElementHandler(valueOfCopyOfInterpreter, SQLProcessor.SQL_NAMESPACE_URI, "value-of");
            addElementHandler(valueOfCopyOfInterpreter, SQLProcessor.SQL_NAMESPACE_URI, "copy-of");
            addElementHandler(new TextInterpreter(getInterpreterContext()), SQLProcessor.SQL_NAMESPACE_URI, "text");
            addElementHandler(new ForEachInterpreter(getInterpreterContext(), getElementHandlers()), SQLProcessor.SQL_NAMESPACE_URI, "for-each");
        }
    }
}