//odtParser.java
//------------------------
//part of YaCy
//(C) by Michael Peter Christen; mc@yacy.net
//first published on http://www.anomic.de
//Frankfurt, Germany, 2005
//
//this file is contributed by Martin Thelian
//
// $LastChangedDate$
// $LastChangedRevision$
// $LastChangedBy$
//
//This program is free software; you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation; either version 2 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program; if not, write to the Free Software
//Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA

package net.yacy.document.parser;

import java.io.File;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import net.yacy.cora.document.MultiProtocolURI;
import net.yacy.cora.document.UTF8;
import net.yacy.document.AbstractParser;
import net.yacy.document.Document;
import net.yacy.document.Parser;
import net.yacy.document.parser.xml.ODContentHandler;
import net.yacy.document.parser.xml.ODMetaHandler;
import net.yacy.kelondro.io.CharBuffer;
import net.yacy.kelondro.util.FileUtils;

public class odtParser extends AbstractParser implements Parser {

    public final static int MAX_DOCSIZE = 200 * 1024 * 1024;

    public odtParser() {
        super("OASIS OpenDocument V2 Text Document Parser");
        this.SUPPORTED_EXTENSIONS.add("odt");
        this.SUPPORTED_EXTENSIONS.add("ods");
        this.SUPPORTED_EXTENSIONS.add("odp");
        this.SUPPORTED_EXTENSIONS.add("odg");
        this.SUPPORTED_EXTENSIONS.add("odc");
        this.SUPPORTED_EXTENSIONS.add("odf");
        this.SUPPORTED_EXTENSIONS.add("odb");
        this.SUPPORTED_EXTENSIONS.add("odi");
        this.SUPPORTED_EXTENSIONS.add("odm");
        this.SUPPORTED_EXTENSIONS.add("ott");
        this.SUPPORTED_EXTENSIONS.add("ots");
        this.SUPPORTED_EXTENSIONS.add("otp");
        this.SUPPORTED_EXTENSIONS.add("otg");
        this.SUPPORTED_EXTENSIONS.add("sxw"); // Star Office Writer file format
        this.SUPPORTED_EXTENSIONS.add("sxc"); // Star Office Calc file format
        this.SUPPORTED_MIME_TYPES.add("application/vnd.oasis.opendocument.text");
        this.SUPPORTED_MIME_TYPES.add("application/vnd.oasis.opendocument.spreadsheet");
        this.SUPPORTED_MIME_TYPES.add("application/vnd.oasis.opendocument.presentation");
        this.SUPPORTED_MIME_TYPES.add("application/vnd.oasis.opendocument.graphics");
        this.SUPPORTED_MIME_TYPES.add("application/vnd.oasis.opendocument.chart");
        this.SUPPORTED_MIME_TYPES.add("application/vnd.oasis.opendocument.formula");
        this.SUPPORTED_MIME_TYPES.add("application/vnd.oasis.opendocument.database");
        this.SUPPORTED_MIME_TYPES.add("application/vnd.oasis.opendocument.image");
        this.SUPPORTED_MIME_TYPES.add("application/vnd.oasis.opendocument.text-master");
        this.SUPPORTED_MIME_TYPES.add("application/vnd.oasis.opendocument.text-template");
        this.SUPPORTED_MIME_TYPES.add("application/vnd.oasis.opendocument.spreadsheet-template");
        this.SUPPORTED_MIME_TYPES.add("application/vnd.oasis.opendocument.presentation-template");
        this.SUPPORTED_MIME_TYPES.add("application/vnd.oasis.opendocument.graphics-template");
        this.SUPPORTED_MIME_TYPES.add("application/x-vnd.oasis.opendocument.text");
        this.SUPPORTED_MIME_TYPES.add("application/OOo-calc");
        this.SUPPORTED_MIME_TYPES.add("application/OOo-writer");
    }

    private Document[] parse(final MultiProtocolURI location, final String mimeType,
            final String charset, final File dest)
            throws Parser.Failure, InterruptedException {

        CharBuffer writer = null;
        try {
            String docDescription = null;
            String docKeywordStr  = null;
            String docShortTitle  = null;
            String docLongTitle   = null;
            String docAuthor      = null;
            String docLanguage    = null;

            // opening the file as zip file
            final ZipFile zipFile = new ZipFile(dest);
            final Enumeration<? extends ZipEntry> zipEnum = zipFile.entries();
            final SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();

            // looping through all containing files
            while (zipEnum.hasMoreElements()) {

                // getting the next zip file entry
                final ZipEntry zipEntry= zipEnum.nextElement();
                final String entryName = zipEntry.getName();

                // content.xml contains the document content in xml format
                if (entryName.equals("content.xml")) {

                    // create a writer for output
                    writer = new CharBuffer(MAX_DOCSIZE, (int)zipEntry.getSize());
                    try {
	                    // extract data
	                    final InputStream zipFileEntryStream = zipFile.getInputStream(zipEntry);
	                    try {
		                    final SAXParser saxParser = saxParserFactory.newSAXParser();
		                    saxParser.parse(zipFileEntryStream, new ODContentHandler(writer));
	                    } finally {
		                    // close readers and writers
		                    zipFileEntryStream.close();
	                    }
                    } finally {
                    	writer.close();
                    }
                } else if (entryName.equals("meta.xml")) {
                    //  meta.xml contains metadata about the document
                    final InputStream zipFileEntryStream = zipFile.getInputStream(zipEntry);
                    final SAXParser saxParser = saxParserFactory.newSAXParser();
                    final ODMetaHandler metaData = new ODMetaHandler();
                    saxParser.parse(zipFileEntryStream, metaData);
                    docDescription = metaData.getDescription();
                    docKeywordStr  = metaData.getKeyword();
                    docShortTitle  = metaData.getTitle();
                    docLongTitle   = metaData.getSubject();
                    docAuthor      = metaData.getCreator();
                    docLanguage    = metaData.getLanguage();
                }
            }

            // make the languages set
            final Set<String> languages = new HashSet<String>(1);
            if (docLanguage != null) languages.add(docLanguage);

            // if there is no title availabe we generate one
            if (docLongTitle == null) {
                if (docShortTitle != null) {
                    docLongTitle = docShortTitle;
                }
            }

            // split the keywords
            String[] docKeywords = null;
            if (docKeywordStr != null) docKeywords = docKeywordStr.split(" |,");

            // create the parser document
            Document[] docs = null;
            final byte[] contentBytes = UTF8.getBytes(writer.toString());
            docs = new Document[]{new Document(
                    location,
                    mimeType,
                    "UTF-8",
                    this,
                    languages,
                    docKeywords,
                    docLongTitle,
                    docAuthor,
                    "",
                    null,
                    docDescription,
                    0.0f, 0.0f,
                    contentBytes,
                    null,
                    null,
                    null,
                    false)};
            return docs;
        } catch (final Exception e) {
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof Parser.Failure) throw (Parser.Failure) e;

            // close the writer
            if (writer != null) try { writer.close(); } catch (final Exception ex) {/* ignore this */}

            throw new Parser.Failure("Unexpected error while parsing odt file. " + e.getMessage(),location);
        }
    }

    @Override
    public Document[] parse(final MultiProtocolURI location, final String mimeType, final String charset, final InputStream source) throws Parser.Failure, InterruptedException {
        File dest = null;
        try {
            // creating a tempfile
            dest = File.createTempFile("OpenDocument", ".odt");
            dest.deleteOnExit();

            // copying the stream into a file
            FileUtils.copy(source, dest);

            // parsing the content
            return parse(location, mimeType, charset, dest);
        } catch (final Exception e) {
            if (e instanceof InterruptedException) throw (InterruptedException) e;
            if (e instanceof Parser.Failure) throw (Parser.Failure) e;

            throw new Parser.Failure("Unexpected error while parsing odt file. " + e.getMessage(),location);
        } finally {
            if (dest != null) FileUtils.deletedelete(dest);
        }
    }

}
