/* Copyright (c) 2001-2005, The HSQL Development Group
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

import java.util.NoSuchElementException;

/**
 * Abstract base for Lists
 *
 * @author fredt@users
 * @version 1.7.2
 * @since 1.7.0
 */
abstract class BaseList {

    protected int elementCount;

    abstract Object get(int index);

    abstract Object remove(int index);

    public boolean isEmpty() {
        return elementCount == 0;
    }

    /** Returns a string representation */
    public String toString() {

        StringBuffer sb = new StringBuffer(32 + elementCount * 3);

        sb.append("List : size=");
        sb.append(elementCount);
        sb.append(' ');
        sb.append('{');

        Iterator it = iterator();

        while (it.hasNext()) {
            sb.append(it.next());

            if (it.hasNext()) {
                sb.append(',');
                sb.append(' ');
            }
        }

        sb.append('}');

        return sb.toString();
    }

    public Iterator iterator() {
        return new BaseListIterator();
    }

    private class BaseListIterator implements Iterator {

        int     counter = 0;
        boolean removed;

        public boolean hasNext() {
            return counter < elementCount;
        }

        public Object next() {

            if (counter < elementCount) {
                removed = false;

                Object returnValue = get(counter);

                counter++;

                return returnValue;
            }

            throw new NoSuchElementException();
        }

        public int nextInt() {
            throw new NoSuchElementException();
        }

        public long nextLong() {
            throw new NoSuchElementException();
        }

        public void remove() {

            if (removed) {
                throw new NoSuchElementException("Iterator");
            }

            removed = true;

            if (counter != 0) {
                BaseList.this.remove(counter - 1);

                counter--;    // above can throw, so decrement if successful

                return;
            }

            throw new NoSuchElementException();
        }
    }
}
