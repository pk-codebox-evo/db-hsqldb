#region licence

/* Copyright (c) 2001-2009, The HSQL Development Group
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

#endregion

#region Using
using CursorConstants = java.sql.ResultSet.__Fields;
#endregion

namespace System.Data.Hsqldb.Common.Enumeration
{
    #region CursorFetchDirection

    /// <summary>
    /// <para>
    /// Specifies the fetch direction of an HSQLDB cursor.
    /// </para>
    /// <img src="../Documentation/ClassDiagrams/System.Data.Hsqldb.Common.Enumeration.CursorFetchDirection.png"
    ///      alt="CursorFetchDirection Class Diagram"/>
    /// </summary>
    /// <author name="boucherb@users"/>
    public enum CursorFetchDirection
    {
        /// <summary>
        /// Specifies the forward fetch direction.
        /// </summary>
        Forward = CursorConstants.FETCH_FORWARD,
        /// <summary>
        /// Specifies the reverse fetch direction.
        /// </summary>
        Reverse = CursorConstants.FETCH_REVERSE,
        /// <summary>
        /// Specifies that the fetch direction is unknown.
        /// </summary>
        Unknown = CursorConstants.FETCH_UNKNOWN
    }

    #endregion
}
