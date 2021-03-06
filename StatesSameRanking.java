import java.util.*;
import java.io.IOException;
import java.util.StringTokenizer;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;


public class StatesSameRanking {
	private static final String temp_data_path = "/temp_data";
  public static class TokenizerMapper
       extends Mapper<Object, Text, Text, Text>{

    private Text word = new Text();
    private String fileName = new String();

    //Get the filename of the file currently being read

    @Override
    protected void setup(Context context) throws IOException, InterruptedException {
      FileSplit fsFileSplit = (FileSplit) context.getInputSplit();
      fileName = ((FileSplit) context.getInputSplit()).getPath().toString();
    }

    //Read line by line and remove any redundant data to form key, value pairs

    public void map(Object key, Text value, Context context
                    ) throws IOException, InterruptedException {
      String[] itr = value.toString().replaceAll("<.*?>", "").replaceAll("[^a-zA-Z ]", " ").toLowerCase().split("\\s+");	//remove tags, and convert all letters to lowercase
      for(String s: itr){
    	  if(s.equals("agriculture")||s.equals("education")||s.equals("sports")||s.equals("politics")){				//check for specific words and write them into file for reducer input
    		  word.set(s);
    		  Text file = new Text(fileName.substring(fileName.lastIndexOf("/") + 1));					//remove path and get only file name
    		  context.write(file, word);
    	  }
      }
    }
  }

  public static class RankState
       extends Reducer<Text,Text,Text,Text> {

    //Find ranking of each word in a single file.    

    public void reduce(Text key, Iterable<Text> values,
                       Context context
                       ) throws IOException, InterruptedException {
      LinkedHashMap<String, Integer> wordMap = new LinkedHashMap<>();
      
      for(Text value: values){
    	  String word = value.toString();
    	  if(wordMap.containsKey(word)){
    		  wordMap.put(word, wordMap.get(word) + 1);     //Check number of times each word appears
    	  }
    	  else{
    		  wordMap.put(word, 1);
    	  }
      }
      TreeMap<Integer, ArrayList<String>> ascendingMap = new TreeMap<>();   //TreeMap to interchange keys with values to form proper ordering for ranking
      
      for(Map.Entry<String, Integer> entry: wordMap.entrySet()){
      	if(ascendingMap.containsKey(entry.getValue())){
      		ascendingMap.get(entry.getValue()).add(entry.getKey());
      	}
      	else{
      		ArrayList<String> temp = new ArrayList<>();
      		temp.add(entry.getKey());
      		ascendingMap.put(entry.getValue(), temp);
      	}
      	
      }
      
      String ranking = "";
      
      for(Map.Entry<Integer, ArrayList<String>> entry: ascendingMap.entrySet()){
      	
      	for(String s: entry.getValue()){
      		 ranking = s + ">" + ranking; 		//form string with TreeMap values in reverse to
      	}						//show word which appears highest to lowest with each word seperated by '>'
      	
      } 
      context.write(new Text(ranking), key);
    }
  }

  public static class FinalMap extends Mapper<LongWritable, Text, Text, Text> {
	  
          //Prepare for next reducer input
	  
          public void map(LongWritable key, Text values, Context context) throws IOException, InterruptedException{
          String line = values.toString();
          String[] temp = line.split("\\s+");
          context.write(new Text(temp[1]), new Text(temp[0]));
     }
	  
  }
  
  
  public static class FinalReduce extends Reducer<Text, Text, Text, Text>{
	  
	  //Check values for each key obtained and form string to get all states which have same ranking for given words

	  public void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException{
		  String states = "";
		  for(Text val: values){
			  states = states + " " + val.toString() + " ";
		  }
		  context.write(key, new Text(states));
	 }
  }
  
  public static void main(String[] args) throws Exception {
    Configuration conf = new Configuration();
    Job job1 = Job.getInstance(conf, "Job 1");
    job1.setJarByClass(StatesSameRanking.class);
    job1.setJar("StatesSameRanking.jar");
    job1.setMapperClass(TokenizerMapper.class);
    job1.setCombinerClass(RankState.class);
    job1.setReducerClass(RankState.class);
    job1.setOutputKeyClass(Text.class);
    job1.setOutputValueClass(Text.class);
    FileInputFormat.addInputPath(job1, new Path(args[0]));
    FileOutputFormat.setOutputPath(job1, new Path(temp_data_path));
    job1.waitForCompletion(true);
    
    Configuration conf2 = new Configuration();
    Job job2 = Job.getInstance(conf, "Job 2");
    job2.setJarByClass(StatesSameRanking.class);
    job2.setJar("StatesSameRanking.jar");
    job2.setMapperClass(FinalMap.class);
    job2.setCombinerClass(FinalReduce.class);
    job2.setReducerClass(FinalReduce.class);
    job2.setOutputKeyClass(Text.class);
    job2.setOutputValueClass(Text.class);

    FileInputFormat.addInputPath(job2, new Path(temp_data_path));
    FileOutputFormat.setOutputPath(job2, new Path(args[1]));
    
    
    System.exit(job2.waitForCompletion(true) ? 0 : 1);
  }
}