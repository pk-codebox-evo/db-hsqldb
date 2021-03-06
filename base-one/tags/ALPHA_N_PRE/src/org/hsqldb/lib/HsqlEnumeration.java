/* Copyright (c) 2001-2002, The HSQL Development Group
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * Neither the name of the HSQL Development Group nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL HSQL DEVELOPMENT GROUP, HSQLDB.ORG, 
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, 
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */


package org.hsqldb.lib;

import java.util.Enumeration;
import java.util.NoSuchElementException;

/** An Enumeration that takes its elements from a specified array
 *
 */
public class HsqlEnumeration implements Enumeration {

    private final static Object[] emptyelements = new Object[0];
    /** the array of objects to enumerate */
    private Object[] elements;

    /** the index of the next element to be enumerated */
    private int i;

    /** return only not null elements */
    private boolean notNull;

    /**
     * Constructs an empty Enumeration. <p>
     */
    public HsqlEnumeration() {
        this.elements = emptyelements;
    }

    /**
     * Constructs an Enumeration for specified array. <p>
     *
     * @param elements the array of objects to enumerate
     */
    public HsqlEnumeration(Object[] elements) {
        this.elements = elements;
    }

    /**
     * Constructs an Enumeration for not-null elements of specified array. <p>
     *
     * @param elements the array of objects to enumerate
     */
    public HsqlEnumeration(Object[] elements, boolean notnull) {
        this.elements = elements;
        notNull       = notnull;
    }

    /**
     * Constructs a Enumeration for a singleton object
     *
     * @param element the single object to enumerate
     */
    public HsqlEnumeration(Object element) {
        this.elements = new Object[1];
        this.elements[0] = element;
    }

    /**
     * Tests if this enumeration contains more elements. <p>
     *
     * @return  <code>true</code> if this enumeration contains more elements;
     *          <code>false</code> otherwise.
     */
    public boolean hasMoreElements() {

        if (elements == null) {
            return false;
        }

        for (; notNull && i < elements.length && elements[i] == null; i++) {}

        if (i < elements.length) {
            return true;
        } else {
            // we are done:
            // release elements for garbage collection
            elements = null;
            return false;
        }

    }

    /**
     * Returns the next element of this enumeration.
     *
     * @return the next element
     * @throws NoSuchElementException if there is no next element
     */
    public Object nextElement() {

        if (hasMoreElements()) {
            return elements[i++];
        }

        throw new NoSuchElementException();

    }
}
