package it.istat.urlscorer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

/**
* @author  Donato Summa
*/
public class UrlScorer {

	static Logger logger = Logger.getLogger(UrlScorer.class);
	
	private String linksFileFolderPath;
	private String firmsInfoFilePath;
	private String provincesFilePath;
	private String solrIndexDirectoryPath;
		
	public static void main(String[] args) throws IOException {
		
		logger.debug("**********************************************************************************************");
		logger.debug("********************     START EXECUTION       ***********************************************");
		logger.debug("**********************************************************************************************");
        String now = Utils.getDateTimeAsString();
        DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        Date startDateTime = new Date();
        logger.info("Starting datetime = " + dateFormat.format(startDateTime)); //15/12/2014 15:59:48
        
		UrlScorer scorer = new UrlScorer();
		scorer.configure(args);
		scorer.calculateAndPrintScore();		
		
		Date endDateTime = new Date();
		logger.info("Started at = " + dateFormat.format(startDateTime)); //15/12/2014 15:59:48
        logger.info("Ending datetime = " + dateFormat.format(endDateTime)); //15/12/2014 15:59:48
        logger.debug("**********************************************************************************************");
		logger.debug("********************     END EXECUTION         ***********************************************");
		logger.debug("**********************************************************************************************");
		
	}

	private void calculateAndPrintScore() throws IOException {
		
		ScoreCalculationStrategy scs = new ScoreCalculationStrategy();
		//File indexDirectory = new File(solrIndexDirectoryPath);
		//IndexReader indexReader = IndexReader.open(FSDirectory.open(indexDirectory));
		
		// per farlo funzionare con Solr_6.6.0
		Path path = Paths.get(solrIndexDirectoryPath);
	    Directory directory = FSDirectory.open(path);		
		IndexReader indexReader = DirectoryReader.open(directory);
		// per farlo funzionare con Solr_6.6.0
		
		IndexSearcher indexSearcher = new IndexSearcher(indexReader);
		
		logger.debug("===> loading from file the list of provinces");
		Utils.loadProvinceListFromFile(provincesFilePath);
		
        logger.debug("===> loading from file the ordered list of firms ids");
		List<Firm> firmsOrderedList = new ArrayList<Firm>();
		firmsOrderedList = Utils.getFirmsOrderedListFromFile(firmsInfoFilePath);
		
		logger.debug("===> creation of the links file that will be filled");
		String now = Utils.getDateTimeAsString();
		File file = new File(linksFileFolderPath + File.separator + Conf.linksFileName + "_" + now + ".txt");
		FileWriter fw = new FileWriter(file);
		BufferedWriter bw = new BufferedWriter(fw);
		bw.write("FIRM_ID"+"\t"+"LINK_POSITION"+"\t"+"URL"+"\t"+"SCORE_VECTOR"+"\t"+"SCORE"); // first line with headers
		bw.newLine();
		
		for(Firm f : firmsOrderedList){
			
			logger.debug("===> retrieving all the firm links having code " + f.getFirmId());
			Term queryTerm = new Term("codiceAzienda", f.getFirmId());
			TermQuery query = new TermQuery(queryTerm);
			TopDocs docs = indexSearcher.search(query, Integer.MAX_VALUE);
			for (ScoreDoc scoreDoc : docs.scoreDocs) {
				Document doc = indexSearcher.doc(scoreDoc.doc);
				Link link = new Link();
				link.setFirmId(doc.get("codiceAzienda"));
				link.setLinkPosition(doc.get("codiceLink"));
				link.setUrl(doc.get("url"));
				int scoreVector = scs.linkScoreVectorCalculation(doc, f);
				link.setScoreVector(scoreVector);
				link.setScore(scs.linkScoreCalculation(String.valueOf(scoreVector)));
				bw.write(link.toString());
				bw.newLine();
	        }	
			
		}
		
		//indexSearcher.close();	
		indexReader.close();		
		bw.close();
		fw.close();
		
	}

	private void configure(String[] args) throws IOException {
		
		if (args.length == 1){
			if (Utils.isAValidFile(args[0])){
				FileInputStream fis = new FileInputStream(args[0]);
				InputStream inputStream = fis;
				Properties props = new Properties();
				props.load(inputStream);
				
				if(props.getProperty("FIRMS_INFO_NUM_COLS") != null){
					props.getProperty("FIRMS_INFO_NUM_COLS");
				}else{
					System.out.println("No custom configuration for the parameter FIRMS_INFO_NUM_COLS");
					System.out.println("The standard value (9) will be assumed");
					System.exit(1);
				}
				
				
				// Mandatory parameters
								
				// LINKS_FILE_FOLDER_PATH
				if(props.getProperty("LINKS_FILE_FOLDER_PATH") != null){
					linksFileFolderPath = props.getProperty("LINKS_FILE_FOLDER_PATH");
					if (!Utils.isAValidDirectory(linksFileFolderPath)){
			        	System.out.println("The LINKS_FILE_FOLDER_PATH parameter that you set ( " + linksFileFolderPath + " ) is not valid");
			        	System.exit(1);
			        }
				}else{
					System.out.println("Wrong/No configuration for the parameter LINKS_FILE_FOLDER_PATH !");
					System.exit(1);
				}
				
				// FIRMS_INFO_FILE_PATH
				if(props.getProperty("FIRMS_INFO_FILE_PATH") != null){
					firmsInfoFilePath = props.getProperty("FIRMS_INFO_FILE_PATH");
					if (!Utils.isAValidFile(firmsInfoFilePath)){
			        	System.out.println("The FIRMS_INFO_FILE_PATH parameter that you set ( " + firmsInfoFilePath + " ) is not valid");
			        	System.exit(1);
			        }
				}else{
					System.out.println("Wrong/No configuration for the parameter FIRMS_INFO_FILE_PATH !");
					System.exit(1);
				}
				
				// PROVINCES_FILE_PATH
				if(props.getProperty("PROVINCES_FILE_PATH") != null){
					provincesFilePath = props.getProperty("PROVINCES_FILE_PATH");
					if (!Utils.isAValidFile(provincesFilePath)){
			        	System.out.println("The PROVINCES_FILE_PATH parameter that you set ( " + provincesFilePath + " ) is not valid");
			        	System.exit(1);
			        }
				}else{
					System.out.println("Wrong/No configuration for the parameter PROVINCES_FILE_PATH !");
					System.exit(1);
				}
				
				// SOLR_INDEX_DIRECTORY_PATH
				if(props.getProperty("SOLR_INDEX_DIRECTORY_PATH") != null){
					solrIndexDirectoryPath = props.getProperty("SOLR_INDEX_DIRECTORY_PATH");
					if (!Utils.isAValidDirectory(solrIndexDirectoryPath)){
			        	System.out.println("The SOLR_INDEX_DIRECTORY_PATH parameter that you set ( " + solrIndexDirectoryPath + " ) is not valid");
			        	System.exit(1);
			        }
				}else{
					System.out.println("Wrong/No configuration for the parameter SOLR_INDEX_DIRECTORY_PATH !");
					System.exit(1);
				}
				
				//TELEPHONE_SCORE = 156
				if(props.getProperty("TELEPHONE_SCORE") == null){
					System.out.println("Wrong/No configuration for the parameter TELEPHONE_SCORE !");	
					System.exit(1);
				}
				if(props.getProperty("SIMPLE_URL_SCORE") == null){
					System.out.println("Wrong/No configuration for the parameter SIMPLE_URL_SCORE !");	
					System.exit(1);
				}
				if(props.getProperty("LINK_POSITION_SCORE") == null){
					System.out.println("Wrong/No configuration for the parameter LINK_POSITION_SCORE !");
					System.exit(1);
				}
				if(props.getProperty("VAT_NUMBER_SCORE") == null){
					System.out.println("Wrong/No configuration for the parameter VAT_NUMBER_SCORE !");	
					System.exit(1);
				}
				if(props.getProperty("MUNICIPALITY_SCORE") == null){
					System.out.println("Wrong/No configuration for the parameter MUNICIPALITY_SCORE !");
					System.exit(1);
				}
				if(props.getProperty("PROVINCE_SCORE") == null){
					System.out.println("Wrong/No configuration for the parameter PROVINCE_SCORE !");	
					System.exit(1);
				}
				if(props.getProperty("ZIP_CODE_SCORE") == null){
					System.out.println("Wrong/No configuration for the parameter ZIP_CODE_SCORE !");
					System.exit(1);
				}
				
				try{
					Conf.setTelephoneScore(Integer.parseInt(props.getProperty("TELEPHONE_SCORE")));
					Conf.setSimpleUrlScore(Integer.parseInt(props.getProperty("SIMPLE_URL_SCORE")));
					Conf.setLinkPositionScore(Integer.parseInt(props.getProperty("LINK_POSITION_SCORE")));
					Conf.setVatNumberScore(Integer.parseInt(props.getProperty("VAT_NUMBER_SCORE")));
					Conf.setMunicipalityScore(Integer.parseInt(props.getProperty("MUNICIPALITY_SCORE")));
					Conf.setProvinceScore(Integer.parseInt(props.getProperty("PROVINCE_SCORE")));
					Conf.setZipCodeScore(Integer.parseInt(props.getProperty("ZIP_CODE_SCORE")));
					Conf.setFirmsInfoNumCols(Integer.parseInt(props.getProperty("FIRMS_INFO_NUM_COLS")));
				}catch(NumberFormatException nfe){
					System.out.println("All the scorer (and input files related) tech parameters must be integers !");
					System.exit(1);
				}
				
			} else {
				System.out.println("Error opening file " + args[0] + " or non-existent file");
				System.out.println("==>  program execution terminated <==");
				System.exit(1);
			}
		} else {
			System.out.println("usage: java -jar UrlScorer.jar [urlScorerConf.properties fullpath]");
			System.exit(1);
		}			
	}


}


