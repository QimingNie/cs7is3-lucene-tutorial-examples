import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.*;
import org.apache.lucene.store.FSDirectory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CranfieldSearcher {

    /*
     * Main
     * Select the scorer based on the input model name and output the scoring file.
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Usage: java CranfieldSearcher <indexDir> <queryFile> <outputDir> [--model=vsm|classic|bm25|lm|dfr] [--maxHits=N]");
            return;
        }

        final String indexDir  = args[0];
        final String queryFile = args[1];
        final String outputDir = args[2];

        String model = null;
        Integer maxHits = null;
        for (String a : args) {
            if (a.startsWith("--model=")) {
                model = a.substring("--model=".length()).toLowerCase(Locale.ROOT);
            } else if (a.startsWith("--maxHits=")) {
                maxHits = Integer.parseInt(a.substring("--maxHits=".length()));
            }
        }
        // legacy positional model at args[3]
        if (model == null && args.length >= 4 && !args[3].isEmpty() && !args[3].startsWith("--")) {
            model = args[3].toLowerCase(Locale.ROOT);
        }
        if (model == null) model = "bm25";

        // legacy positional maxHits at args[4]
        if (maxHits == null && args.length >= 5 && !args[4].startsWith("--")) {
            try { maxHits = Integer.parseInt(args[4]); } catch (Exception ignored) {}
        }
        if (maxHits == null) maxHits = 1000;

        //Similarity and output filename
        Similarity similarity;
        String outName;
        switch (model) {
            case "classic":
            case "vsm":
                similarity = new ClassicSimilarity();
                outName = "run_classic.txt"; // treat vsm as classic
                System.out.println("Selected model: CLASSIC");
                break;
            case "bm25":
                similarity = new BM25Similarity();
                outName = "run_bm25.txt";
                System.out.println("Selected model: BM25");
                break;
            case "lm":
            case "lmdirichlet":
                similarity = new LMDirichletSimilarity();
                outName = "run_lm.txt";
                System.out.println("Selected model: LMDirichlet");
                break;
            case "dfr":
                similarity = new DFRSimilarity(new BasicModelIF(), new AfterEffectB(), new NormalizationH2());
                outName = "run_dfr.txt";
                System.out.println("Selected model: DFR");
                break;
            default:
                System.out.println("Unknown model '" + model + "', defaulting to BM25");
                similarity = new BM25Similarity();
                outName = "run_bm25.txt";
        }

        //Ensure output directory and path
        Path outDir = Paths.get(outputDir);
        Files.createDirectories(outDir);
        Path outPath = outDir.resolve(outName);

        //Open index reader & searcher with chosen Similarity
        DirectoryReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexDir)));
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(similarity);
        Analyzer analyzer = new EnglishAnalyzer();
        QueryParser parser = new QueryParser("content", analyzer); // match the indexer’s aggregated field

        //Read queries
        List<String> queries = readCranfieldQueries(queryFile);

        //Execute search and write TREC output
        try (BufferedWriter writer = new BufferedWriter(
                new OutputStreamWriter(Files.newOutputStream(outPath), StandardCharsets.UTF_8))) {

            for (int qid = 1; qid <= queries.size(); qid++) {
                String qtext = queries.get(qid - 1);
                if (qtext == null || qtext.isBlank()) continue;

                // Escape special chars and parse into a Lucene Query
                Query query = parser.parse(QueryParser.escape(qtext));

                // Search top N
                TopDocs results = searcher.search(query, maxHits);
                ScoreDoc[] hits = results.scoreDocs;

                // Write TREC lines
                for (int rank = 0; rank < hits.length; rank++) {
                    Document doc = searcher.storedFields().document(hits[rank].doc);
                    String docno = doc.get("docno"); 
                    // Avoid having docno be empty
                    if (docno == null || docno.isEmpty()) {
                        docno = String.valueOf(hits[rank].doc); 
                    }
                    float score = hits[rank].score;

                    // TREC: qid Q0 docno rank score tag
                    writer.write(String.format(Locale.ROOT,
                            "%d Q0 %s %d %.6f lucene-%s%n",
                            qid, docno, rank + 1, score, model));
                }
            }
        }

        reader.close();
        System.out.println("Search completed. Output saved to: " + outPath);
    }

    /*
     * Iterate lines in queryFile.
     * Return list of query texts in order (qid = index + 1).
     */
    private static List<String> readCranfieldQueries(String queryFile) throws IOException {
        List<String> queries = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(queryFile), StandardCharsets.UTF_8))) {
            String line;
            StringBuilder cur = null;
            boolean inW = false;

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.startsWith(".I")) {
                    if (cur != null) {
                        queries.add(cur.toString().trim());
                    }
                    cur = new StringBuilder();
                    inW = false;
                } else if (line.equals(".W")) {
                    inW = true;
                } else if (inW && cur != null) {
                    if (!line.isEmpty()) {
                        if (cur.length() > 0) cur.append(' ');
                        cur.append(line);
                    }
                }
            }
            if (cur != null) {
                queries.add(cur.toString().trim());
            }
        }
        return queries;
    }
}

