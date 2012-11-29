package org.openimaj.twitter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.openimaj.io.FileUtils;
import org.openimaj.rdf.utils.PQUtils;
import org.openimaj.twitter.USMFStatus.Link;
import org.openimaj.twitter.USMFStatus.User;
import org.openjena.riot.SysRIOT;

import com.hp.hpl.jena.query.ParameterizedSparqlString;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.update.UpdateAction;
import com.hp.hpl.jena.update.UpdateRequest;

/**
 * Holds an internal Jena Graph of the USMF status. The default language used is
 * NTriples
 * 
 * @author Jon Hare (jsh2@ecs.soton.ac.uk), Sina Samangooei (ss@ecs.soton.ac.uk)
 * 
 */
public class GeneralJSONRDF extends GeneralJSON {

	enum Variables {
		SERVICE("service"),
		SOCIAL_EVENT("socialEvent"),
		USER("user"),
		PERSON("person"),
		PERSON_NAME("realname"),
		PERSON_LOC("location"),
		PERSON_LAT("lat"),
		PERSON_LONG("long"),
		USER_NAME("username"),
		USER_ID("osnid"),
		USER_LANG("userlanguage"),
		PERSON_LANG("personlanguage"),
		USER_DESC("description"),
		USER_AVATAR("useravatar"),
		USER_SITE("website"),
		USER_PROF("profile"),
		USER_FOLLOWERS("subscribers"),
		USER_FOLLOWING("subscriptions"),
		SOURCE_URL("sourceURL"),
		TEXT("text"),
		DESC("description"),
		CAT("category"),
		FAV("favourites"),
		USER_POSTS("postings"), LINK("link"), KEYWORD("keyword"), ;
		public String name;

		private Variables(String name) {
			this.name = name;
		}

	}

	//	private static final String ITEM_QUERY_FILE = "/org/openimaj/twitter/rdf/usmf_query.sparql";
	private static final String INSERT_ITEM_QUERY_FILE = "/org/openimaj/twitter/rdf/insert_usmf_query.sparql";
	private static final String DELETE_TM_NULL = "/org/openimaj/twitter/rdf/delete_null.sparql";
	private static final String LINK_INSERT_QUERY_FILE = "/org/openimaj/twitter/rdf/insert_usmf_links_query.sparql";
	private static final String KEYWORDS_INSERT_QUERY_FILE = "/org/openimaj/twitter/rdf/insert_usmf_keywords_query.sparql";
	private static final String TOUSERS_INSERT_QUERY_FILE = "/org/openimaj/twitter/rdf/insert_usmf_touser_query.sparql";;
	private static Map<String, String> queryCache;

	static {
		SysRIOT.wireIntoJena();
	}

	private Model m;
	private String eventIRI;
	private static final Map<String, RDFAnalysisProvider<?>> providers = new HashMap<String, RDFAnalysisProvider<?>>();

	/**
	 * Registers an analysis provider to be used when some analysis key is met
	 * 
	 * @param analysis
	 * @param analysisProvider
	 */
	public static void registerRDFAnalysisProvider(String analysis, RDFAnalysisProvider<?> analysisProvider) {
		analysisProvider.init();
		providers.put(analysis, analysisProvider);
	}

	@Override
	public void readASCII(final Scanner in) throws IOException {
		StringBuffer b = new StringBuffer();
		while (in.hasNext()) {
			b.append(in.next());
		}
		InputStream stream = new ByteArrayInputStream(b.toString().getBytes("UTF-8"));
		m = ModelFactory.createDefaultModel();
		m.read(stream, "", "NTRIPLES");
		m.close();
	}

	@Override
	public void fillUSMF(USMFStatus status) {
		throw new UnsupportedOperationException("Not supported yet");
	}

	private static String queryCache(String queryFile) {
		if (queryCache == null) {
			queryCache = new HashMap<String, String>();
		}
		String q = queryCache.get(queryFile);
		if (q == null) {
			queryCache.put(queryFile, q = readQuery(queryFile));
		}
		return q;
	}

	private static String readQuery(String qf) {
		try {
			return FileUtils.readall(GeneralJSONRDF.class.getResourceAsStream(qf));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void fromUSMF(USMFStatus status) {
		prepareModel();
		//		m.add(
		//			ResourceFactory.createResource("dc:wangSub"),
		//			ResourceFactory.createProperty("dc:wangPre"),
		//			"wangObj"
		//		);
		ParameterizedSparqlString pss = PQUtils.constructPQ(queryCache(INSERT_ITEM_QUERY_FILE), m);
		this.eventIRI = generateSocialEventIRI(status);
		PQUtils.setPSSIri(pss, Variables.SOCIAL_EVENT.name, eventIRI);
		PQUtils.setPSSLiteral(pss, Variables.SERVICE.name, status.service);
		addUserParameters(pss, status.user, status);
		PQUtils.setPSSLiteral(pss, Variables.SOURCE_URL.name, status.source);
		PQUtils.setPSSLiteral(pss, Variables.TEXT.name, status.text);
		PQUtils.setPSSLiteral(pss, Variables.DESC.name, status.description);
		PQUtils.setPSSLiteral(pss, Variables.CAT.name, status.category);
		PQUtils.setPSSLiteral(pss, Variables.FAV.name, status.favorites);
		UpdateAction.execute(pss.asUpdate(), m);
		pss = PQUtils.constructPQ(readQuery(TOUSERS_INSERT_QUERY_FILE), m);
		// the inreply user

		// the mentioned users
		for (User key : status.to_users) {
			PQUtils.setPSSIri(pss, Variables.SOCIAL_EVENT.name, eventIRI);
			addUserParameters(pss, key, status);
			UpdateAction.execute(pss.asUpdate(), m);
			pss.clearParams();
		}
		pss = PQUtils.constructPQ(readQuery(LINK_INSERT_QUERY_FILE), m);
		PQUtils.setPSSIri(pss, Variables.SOCIAL_EVENT.name, eventIRI);
		for (Link link : status.links) {
			PQUtils.setPSSLiteral(pss, Variables.LINK.name, link.href);
			UpdateAction.execute(pss.asUpdate(), m);
		}
		pss = PQUtils.constructPQ(readQuery(KEYWORDS_INSERT_QUERY_FILE), m);
		PQUtils.setPSSIri(pss, Variables.SOCIAL_EVENT.name, eventIRI);
		for (String key : status.keywords) {
			PQUtils.setPSSLiteral(pss, Variables.KEYWORD.name, key);
			UpdateAction.execute(pss.asUpdate(), m);
		}

		cleanupModel();
		status.fillAnalysis(this);
	}

	private void cleanupModel() {
		UpdateRequest del = PQUtils.constructPQ(readQuery(DELETE_TM_NULL), m).asUpdate();
		UpdateAction.execute(del, m);
	}

	private void addUserParameters(ParameterizedSparqlString pss, User user, USMFStatus status) {
		PQUtils.setPSSIri(pss, Variables.USER.name, createUserIRI(status, user));
		PQUtils.setPSSIri(pss, Variables.PERSON.name, createPersonIRI(status, user));
		PQUtils.setPSSLiteral(pss, Variables.PERSON_NAME.name, user.real_name);
		PQUtils.setPSSLiteral(pss, Variables.PERSON_LOC.name, user.location);
		PQUtils.setPSSLiteral(pss, new String[] { Variables.PERSON_LAT.name, Variables.PERSON_LONG.name }, user.geo);
		PQUtils.setPSSLiteral(pss, Variables.USER_NAME.name, user.name);
		PQUtils.setPSSLiteral(pss, Variables.USER_ID.name, user.id);
		PQUtils.setPSSLiteral(pss, Variables.USER_LANG.name, user.language);
		PQUtils.setPSSLiteral(pss, Variables.PERSON_LANG.name, user.language);
		PQUtils.setPSSLiteral(pss, Variables.USER_DESC.name, user.description);
		PQUtils.setPSSLiteral(pss, Variables.USER_AVATAR.name, user.avatar);
		PQUtils.setPSSLiteral(pss, Variables.USER_SITE.name, user.website);
		PQUtils.setPSSLiteral(pss, Variables.USER_PROF.name, user.profile);
		PQUtils.setPSSLiteral(pss, Variables.USER_FOLLOWERS.name, user.subscribers);
		PQUtils.setPSSLiteral(pss, Variables.USER_FOLLOWING.name, user.subscriptions);
		PQUtils.setPSSLiteral(pss, Variables.USER_POSTS.name, user.postings);
	}

	@Override
	public void writeASCIIAnalysis(PrintWriter outputWriter, List<String> selectiveAnalysis, List<String> selectiveStatus) {
		if (selectiveAnalysis == null) {
			selectiveAnalysis = new ArrayList<String>();
			selectiveAnalysis.addAll(this.analysis.keySet());
		}
		Resource analysisNode = m.createResource();
		for (String ana : selectiveAnalysis) {
			RDFAnalysisProvider<?> prov = providers.get(ana);
			if (prov == null)
				continue;
			prov.addAnalysis(m, analysisNode, this);
		}
		m.add(ResourceFactory.createResource(eventIRI), ResourceFactory.createProperty("tma:analysis"), analysisNode);

		m.write(System.out, "N-TRIPLES");
	}

	private String createUserIRI(USMFStatus status, User user) {
		return String.format("%s%s/user/%s", m.getNsPrefixURI("tm"), status.service, (long) user.id);
	}

	private String createPersonIRI(USMFStatus status, User user) {
		return String.format("%s%s/person/%s", m.getNsPrefixURI("tm"), status.service, (long) user.id);
	}

	private String generateSocialEventIRI(USMFStatus status) {

		return String.format("%s%s/%s", m.getNsPrefixURI("tm"), status.service, status.id);
	}

	private void prepareModel() {
		m = ModelFactory.createDefaultModel();
		m.read(GeneralJSONRDF.class.getResourceAsStream("rdf/base_usmf.rdf"), "");
	}

}
