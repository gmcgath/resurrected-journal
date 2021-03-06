/** Resurrected Journal
 * 
 * Copyright (c) 2012, Gary McGath
 * All rights reserved.
 * 
 * The developer of this software may be available for enhancements or
 * related development work. See http://www.garymcgath.com for current status.
 * 
 * Licensed under the BSD license:
 *
 * Redistribution and use in source and binary forms, with or without modification, 
 * are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this 
 *  list of conditions and the following disclaimer.
 *  Redistributions in binary form must reproduce the above copyright notice, 
 *  this list of conditions and the following disclaimer in the documentation 
 *  and/or other materials provided with the distribution.
 *  
 *  Neither the name of Gary McGath nor the names of contributors 
 *  may be used to endorse or promote products derived from this software 
 *  without specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
 *  AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, 
 *  THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR 
 *  PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR 
 *  CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, 
 *  EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, 
 *  PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR 
 *  PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF 
 *  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING 
 *  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS 
 *  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.mcgath.rj;

/** This takes lj and lj-cut tags and cleans them up. */
public class LJTagCleaner {

    String source;       // source string
    String lcSource;     // Lower-case version of source string
    int sourceIndex;      // index into source string
    int sourceLength;     // length of the source string
    StringBuffer destBuf;
    
    /** Constructor 
     * 
     *  @param s   Source string
     */
    public LJTagCleaner (String s) {
        source = s;
    }
    
    
    /** Convert the source string to a cleaned-up string.
     *  May return the original string if no changes are needed. */
    public String cleanText () {
        // Time-saving check.
        if (source.indexOf ("<lj") < 0) {
            return source;
        }
        
        sourceLength = source.length ();
        
        // Create a mirror string which is all lower case, to facilitate tag matching
        lcSource = source.toLowerCase ();
        destBuf = new StringBuffer (sourceLength);
        sourceIndex = 0;
        for (;;) {
            // Copy all characters up to the next tag of interest.
            int tidx = lcSource.indexOf ("<lj", sourceIndex);
            int ctidx = lcSource.indexOf ("</lj", sourceIndex);
            if (ctidx > 0 && (tidx < 0 || tidx > ctidx)) {
                // The next event of interest is a close tag
                // TODO process close tag
                copyUpToIndex (ctidx);
                skipLJCloseTag();
            }
            else if (tidx > 0) {
                // The next event of interest is an open tag
                copyUpToIndex (tidx);
                if (sourceLength - sourceIndex >= 7 && "<lj-cut".equals(lcSource.substring (sourceIndex, sourceIndex+7))) {
                    processLJCutTag ();
                }
                else {
                    processLJTag ();
                }
            }
            else {
                // All done. Copy the rest and return it.
                copyUpToIndex (sourceLength);
                break;
            }
        }
        return destBuf.toString ();
    }
    
    /** We have a cut tag at sourceIndex. Just discard it. 
     */
    private void processLJCutTag () {
        // Doing nothing is OK for now. Just advance past the tag.
        // LJ seems to convert attribute quotes to double quotes,
        // but let's be safe and check both.
        sourceIndex += 7;    // skip tag name
        for (;;) {
            if (sourceIndex >= sourceLength) {
                break;
            }
            char ch = source.charAt (sourceIndex);
            if (ch == '\'' || ch == '"') {
                obtainQuotedString(ch);  // Just throw it away
                continue;
            }
            else if (ch == '>') {
                sourceIndex++;
                break;
            }
            sourceIndex++;
        }
    }

    // For the lj tag, we look for the "user" parameter and turn its value
    // into a specially styled piece of text.
    private void processLJTag () {
        String userName = null;
        boolean inUserParam = false;
        boolean awaitingUserParam = false;
        // Skip over white space
        sourceIndex += 3;
        skipWhiteSpace ();
        // LJ seems to convert attribute quotes to double quotes,
        // but let's be safe and check both.
        for (;;) {
            if (sourceIndex >= sourceLength) {
                return;
            }
            char ch = source.charAt (sourceIndex);
            if (ch == '\'' || ch == '"') {
                String paramVal = obtainQuotedString(ch);  
                if (awaitingUserParam) {
                    userName = paramVal;
                    inUserParam = false;
                    awaitingUserParam = false;
                    continue;
                }
            }
            else if (ch == '>') {
                sourceIndex++;
                break;
            }
            else if (Character.isLetter(ch)) {
                String name = obtainName();
                if ("user".equals (name)) {
                    inUserParam = true;
                }
                continue;
            }
            else if (ch == '=' && inUserParam) {
                awaitingUserParam = true;
            }
            sourceIndex++;
        }
        
        if (userName != null && !userName.isEmpty()) {
            destBuf.append ("<span class=\"uname\">" + userName + "</span>");
        }
    }
    
    /* sourceIndex is at a whitespace character. Advance it to the next
     * non-whitespace character. */
    private void skipWhiteSpace () {
        while (sourceIndex < sourceLength) {
            if (sourceIndex >= sourceLength) {
                return;
            }
            char ch = source.charAt (sourceIndex);
            if (!Character.isWhitespace(ch)) {
                break;
            }
            sourceIndex++;
        }
    }
    
    /* Skip any close tag that starts with lj */
    private void skipLJCloseTag () {
        if ("</lj".equals (lcSource.substring(sourceIndex, sourceIndex+4))) {
            while (sourceIndex < sourceLength) {
                char ch = source.charAt (sourceIndex++);
                if (ch == '>') {
                    break;
                }
            }
        }
    }
    
    /** Gather consecutive letters and digits into a string, forcing
     *  it to lower case. */
    private String obtainName () {
        StringBuffer nameBuf = new StringBuffer ();
        while (sourceIndex < sourceLength) {
            char ch = lcSource.charAt (sourceIndex);
            if (Character.isLetterOrDigit(ch)) {
                nameBuf.append (ch);
            }
            else {
                break;
            }
            sourceIndex++;
        }
        return nameBuf.toString ();
    }
    
    /* Starting at a single or double quote, gather a string */
    private String obtainQuotedString (char quoteChar) {
        StringBuffer buf = new StringBuffer ();
        sourceIndex++;      // skip the quote char
        while (sourceIndex < sourceLength) {
            char ch = source.charAt (sourceIndex++);
            if (ch == quoteChar) {
                 break;
            }
            buf.append (ch);
        }
        return buf.toString();
    }
    
    /* Copy all characters up to a specified index, and advance
     * sourceIndex to that point. */
    private void copyUpToIndex (int idx) {
        while (sourceIndex < idx) {
            destBuf.append (source.charAt (sourceIndex++));
        }
    }
}
