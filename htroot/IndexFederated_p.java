/**
 *  IndexFederated_p
 *  Copyright 2011 by Michael Peter Christen, mc@yacy.net, Frankfurt am Main, Germany
 *  First released 25.05.2011 at http://yacy.net
 *
 *  $LastChangedDate: 2011-04-14 00:04:23 +0200 (Do, 14 Apr 2011) $
 *  $LastChangedRevision: 7653 $
 *  $LastChangedBy: orbiter $
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Iterator;

import net.yacy.cora.document.UTF8;
import net.yacy.cora.protocol.RequestHeader;
import net.yacy.cora.services.federated.solr.ShardSelection;
import net.yacy.cora.services.federated.solr.ShardSolrConnector;
import net.yacy.cora.services.federated.solr.RemoteSolrConnector;
import net.yacy.cora.services.federated.solr.SolrConnector;
import net.yacy.cora.services.federated.yacy.ConfigurationSet;
import net.yacy.cora.services.federated.yacy.YaCySchema;
import net.yacy.kelondro.logging.Log;
import net.yacy.kelondro.util.OS;
import net.yacy.search.Switchboard;
import net.yacy.search.SwitchboardConstants;
import net.yacy.server.serverObjects;
import net.yacy.server.serverSwitch;

public class IndexFederated_p {

    public static serverObjects respond(@SuppressWarnings("unused") final RequestHeader header, final serverObjects post, final serverSwitch env) {
        // return variable that accumulates replacements
        final serverObjects prop = new serverObjects();
        final Switchboard sb = (Switchboard) env;

        if (post != null && post.containsKey("set")) {
            // yacy
            boolean post_core_rwi = post.getBoolean(SwitchboardConstants.CORE_SERVICE_RWI);
            final boolean previous_core_rwi = sb.index.connectedRWI() && env.getConfigBool(SwitchboardConstants.CORE_SERVICE_RWI, false);
            env.setConfig(SwitchboardConstants.CORE_SERVICE_RWI, post_core_rwi);
            if (previous_core_rwi && !post_core_rwi) sb.index.disconnectRWI(); // switch off
            if (!previous_core_rwi && post_core_rwi) try {
                final int wordCacheMaxCount = (int) sb.getConfigLong(SwitchboardConstants.WORDCACHE_MAX_COUNT, 20000);
                final long fileSizeMax = (OS.isWindows) ? sb.getConfigLong("filesize.max.win", Integer.MAX_VALUE) : sb.getConfigLong( "filesize.max.other", Integer.MAX_VALUE);
                sb.index.connectRWI(wordCacheMaxCount, fileSizeMax);
            } catch (IOException e) { Log.logException(e); } // switch on

            boolean post_core_citation = post.getBoolean(SwitchboardConstants.CORE_SERVICE_CITATION);
            final boolean previous_core_citation = sb.index.connectedCitation() && env.getConfigBool(SwitchboardConstants.CORE_SERVICE_CITATION, false);
            env.setConfig(SwitchboardConstants.CORE_SERVICE_CITATION, post_core_citation);
            if (previous_core_citation && !post_core_citation) sb.index.disconnectCitation(); // switch off
            if (!previous_core_citation && post_core_citation) try {
                final int wordCacheMaxCount = (int) sb.getConfigLong(SwitchboardConstants.WORDCACHE_MAX_COUNT, 20000);
                final long fileSizeMax = (OS.isWindows) ? sb.getConfigLong("filesize.max.win", Integer.MAX_VALUE) : sb.getConfigLong( "filesize.max.other", Integer.MAX_VALUE);
                sb.index.connectCitation(wordCacheMaxCount, fileSizeMax);
            } catch (IOException e) { Log.logException(e); } // switch on

            boolean post_core_fulltext = post.getBoolean(SwitchboardConstants.CORE_SERVICE_FULLTEXT);
            final boolean previous_core_fulltext = sb.index.fulltext().connectedLocalSolr() && env.getConfigBool(SwitchboardConstants.CORE_SERVICE_FULLTEXT, false);
            env.setConfig(SwitchboardConstants.CORE_SERVICE_FULLTEXT, post_core_fulltext);

            final int commitWithinMs = post.getInt("solr.indexing.commitWithinMs", env.getConfigInt(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_COMMITWITHINMS, 180000));
            if (previous_core_fulltext && !post_core_fulltext) {
                // switch off
                sb.index.fulltext().disconnectLocalSolr();
                sb.index.fulltext().disconnectUrlDb();
            }
            if (!previous_core_fulltext && post_core_fulltext) {
                // switch on
                sb.index.connectUrlDb(sb.useTailCache, sb.exceed134217727);
                try { sb.index.fulltext().connectLocalSolr(commitWithinMs); } catch (IOException e) { Log.logException(e); }
            }

            // solr
            final boolean solrRemoteWasOn = sb.index.fulltext().connectedRemoteSolr() && env.getConfigBool(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_ENABLED, true);
            final boolean solrRemoteIsOnAfterwards = post.getBoolean("solr.indexing.solrremote");
            env.setConfig(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_ENABLED, solrRemoteIsOnAfterwards);
            String solrurls = post.get("solr.indexing.url", env.getConfig(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_URL, "http://127.0.0.1:8983/solr"));
            boolean lazy = post.getBoolean("solr.indexing.lazy");
            final BufferedReader r = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(UTF8.getBytes(solrurls))));
            final StringBuilder s = new StringBuilder();
            String s0;
            try {
                while ((s0 = r.readLine()) != null) {
                    s0 = s0.trim();
                    if (s0.length() > 0) {
                        s.append(s0).append(',');
                    }
                }
            } catch (final IOException e1) {
            }
            if (s.length() > 0) {
                s.setLength(s.length() - 1);
            }
            solrurls = s.toString().trim();
            env.setConfig(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_URL, solrurls);
            env.setConfig(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_COMMITWITHINMS, commitWithinMs);
            env.setConfig(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_LAZY, lazy);
            env.setConfig(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_SHARDING, post.get("solr.indexing.sharding", env.getConfig(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_SHARDING, "modulo-host-md5")));
            final String schemename = post.get("solr.indexing.schemefile", env.getConfig(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_SCHEMEFILE, "solr.keys.default.list"));
            env.setConfig(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_SCHEMEFILE, schemename);

            if (solrRemoteWasOn && !solrRemoteIsOnAfterwards) {
                // switch off
                sb.index.fulltext().disconnectRemoteSolr();
            }

            if (!solrRemoteWasOn && solrRemoteIsOnAfterwards) {
                // switch on
                final boolean usesolr = sb.getConfigBool(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_ENABLED, false) & solrurls.length() > 0;
                try {
                    if (usesolr) {
                        SolrConnector solr = new ShardSolrConnector(solrurls, ShardSelection.Method.MODULO_HOST_MD5, 10000, true);
                        solr.setCommitWithinMs(commitWithinMs);
                        sb.index.fulltext().connectRemoteSolr(solr);
                    } else {
                        sb.index.fulltext().disconnectRemoteSolr();
                    }
                } catch (final IOException e) {
                    Log.logException(e);
                    sb.index.fulltext().disconnectRemoteSolr();
                }
            }

            // read index scheme table flags
            final Iterator<ConfigurationSet.Entry> i = sb.index.fulltext().getSolrScheme().entryIterator();
            ConfigurationSet.Entry entry;
            boolean modified = false; // flag to remember changes
            while (i.hasNext()) {
                entry = i.next();
                final String v = post.get("scheme_" + entry.key());
                final String sfn = post.get("scheme_solrfieldname_" + entry.key());
                if (sfn != null ) {
                    // set custom solr field name
                    if (!sfn.equals(entry.getValue())) {
                        entry.setValue(sfn);
                        modified = true;
                    }
                }
                // set enable flag
                final boolean c = v != null && v.equals("checked");
                if (entry.enabled() != c) {
                    entry.setEnable(c);
                    modified = true;
                }
            }
            if (modified) { // save settings to config file if modified
                try {
                    sb.index.fulltext().getSolrScheme().commit();
                    modified = false;
                } catch (IOException ex) {}
            }
        }

        // show solr host table
        if (!sb.index.fulltext().connectedRemoteSolr()) {
            prop.put("table", 0);
        } else {
            prop.put("table", 1);
            final SolrConnector solr = sb.index.fulltext().getRemoteSolr();
            final long[] size = (solr instanceof ShardSolrConnector) ? ((ShardSolrConnector) solr).getSizeList() : new long[]{((RemoteSolrConnector) solr).getSize()};
            final String[] urls = (solr instanceof ShardSolrConnector) ? ((ShardSolrConnector) solr).getAdminInterfaceList() : new String[]{((RemoteSolrConnector) solr).getAdminInterface()};
            boolean dark = false;
            for (int i = 0; i < size.length; i++) {
                prop.put("table_list_" + i + "_dark", dark ? 1 : 0); dark = !dark;
                prop.put("table_list_" + i + "_url", urls[i]);
                prop.put("table_list_" + i + "_size", size[i]);
            }
            prop.put("table_list", size.length);
        }

        // write scheme
        final String schemename = sb.getConfig(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_SCHEMEFILE, "solr.keys.default.list");

        int c = 0;
        boolean dark = false;
        // use enum SolrField to keep defined order
        for(YaCySchema field : YaCySchema.values()) {
            prop.put("scheme_" + c + "_dark", dark ? 1 : 0); dark = !dark;
            prop.put("scheme_" + c + "_checked", sb.index.fulltext().getSolrScheme().contains(field.name()) ? 1 : 0);
            prop.putHTML("scheme_" + c + "_key", field.name());
            prop.putHTML("scheme_" + c + "_solrfieldname",field.name().equalsIgnoreCase(field.getSolrFieldName()) ? "" : field.getSolrFieldName());
            if (field.getComment() != null) prop.putHTML("scheme_" + c + "_comment",field.getComment());
            c++;
        }
        prop.put("scheme", c);

        // fill attribute fields
        // allowed values are: classic, solr, off
        // federated.service.yacy.indexing.engine = classic

        prop.put(SwitchboardConstants.CORE_SERVICE_FULLTEXT + ".checked", env.getConfigBool(SwitchboardConstants.CORE_SERVICE_FULLTEXT, false) ? 1 : 0);
        prop.put(SwitchboardConstants.CORE_SERVICE_RWI + ".checked", env.getConfigBool(SwitchboardConstants.CORE_SERVICE_RWI, false) ? 1 : 0);
        prop.put(SwitchboardConstants.CORE_SERVICE_CITATION + ".checked", env.getConfigBool(SwitchboardConstants.CORE_SERVICE_CITATION, false) ? 1 : 0);
        prop.put("solr.indexing.solrremote.checked", env.getConfigBool(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_ENABLED, false) ? 1 : 0);
        prop.put("solr.indexing.url", env.getConfig(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_URL, "http://127.0.0.1:8983/solr").replace(",", "\n"));
        prop.put("solr.indexing.commitWithinMs", env.getConfigInt(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_COMMITWITHINMS, 180000));
        prop.put("solr.indexing.lazy.checked", env.getConfigBool(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_LAZY, true) ? 1 : 0);
        prop.put("solr.indexing.sharding", env.getConfig(SwitchboardConstants.FEDERATED_SERVICE_SOLR_INDEXING_SHARDING, "modulo-host-md5"));
        prop.put("solr.indexing.schemefile", schemename);

        // return rewrite properties
        return prop;
    }
}
