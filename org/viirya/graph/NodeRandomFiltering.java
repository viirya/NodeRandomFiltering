
package org.viirya.graph;

import java.io.*;
import java.util.*;

//import java.util.Map;
//import java.util.StringTokenizer;
//import java.util.HashMap;
//import java.util.ArrayList;

import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.FloatWritable;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.Text;
//import org.apache.hadoop.mapred.MapReduceBase;
//import org.apache.hadoop.mapred.Mapper;
//import org.apache.hadoop.mapred.OutputCollector;
//import org.apache.hadoop.mapred.Reducer;
//import org.apache.hadoop.mapred.Reporter;
import org.apache.hadoop.fs.*;
//import org.apache.hadoop.mapred.JobConf;
//import org.apache.hadoop.mapred.JobClient;
//import org.apache.hadoop.mapred.FileInputFormat;
//import org.apache.hadoop.mapred.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.*;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.compress.GzipCodec;
import org.apache.hadoop.io.compress.CompressionCodec;


public class NodeRandomFiltering {

/*
    public static class SimpleMapReduceBase extends MapReduceBase {
        JobConf job;
        @Override
        public void configure(JobConf job) {
            super.configure(job);
            this.job = job;
        }

        public StringTokenizer tokenize(String line, String pattern) {
            StringTokenizer tokenizer = new StringTokenizer(line, pattern);
            return tokenizer;
        } 

        public StringTokenizer tokenize(Text value, String pattern) {
            String line = value.toString();
            StringTokenizer tokenizer = new StringTokenizer(line, pattern);
            return tokenizer;
        }
    }
*/

    public static StringTokenizer tokenize(String line, String pattern) {
        StringTokenizer tokenizer = new StringTokenizer(line, pattern);
        return tokenizer;
    } 

    public static StringTokenizer tokenize(Text value, String pattern) {
        String line = value.toString();
        StringTokenizer tokenizer = new StringTokenizer(line, pattern);
        return tokenizer;
    }
 
    public static class NodeCountingMapper extends Mapper<LongWritable, Text, IntWritable, IntWritable> {
        public void map(LongWritable key, Text value, Context context) throws NumberFormatException, IOException, InterruptedException {

            StringTokenizer image_id_tokenizer = tokenize(value, " %");
            if (image_id_tokenizer.countTokens() == 1)
                return;

            context.write(new IntWritable(0), new IntWritable(1));

        }
    }
 
    public static class NodeCountingReducer extends Reducer<IntWritable, IntWritable, IntWritable, IntWritable> {
        public void reduce(IntWritable key, Iterable<IntWritable> values, Context context) throws IOException, InterruptedException {

            int count = 0;
            for (IntWritable val: values) {
                count++;
            }

            try {
                FileSystem fs;
                fs = FileSystem.get(context.getConfiguration());
                String path_str = context.getConfiguration().get("path");

                Path path_data_number_output = new Path(path_str);
                if(!fs.exists(path_data_number_output)) {
                    DataOutputStream out = fs.create(path_data_number_output);
                    out.writeInt(count);
                    out.close();
                }

            } catch(Exception e) {
                throw new IOException(e.getMessage());
            }
            
       

        }
    }
 
    public static class NodeRandomFilteringMapper extends Mapper<LongWritable, Text, IntWritable, Text> {
        public void map(LongWritable key, Text value, Context context) throws NumberFormatException, IOException, InterruptedException {
 
            StringTokenizer image_id_tokenizer = tokenize(value, " %");
            if (image_id_tokenizer.countTokens() == 1)
                return;
            String image_features = image_id_tokenizer.nextToken();
            String image_id = image_id_tokenizer.nextToken();

            Random generator = new Random(Long.parseLong(image_id));
            int rand = generator.nextInt();

            context.write(new IntWritable(0), new Text(rand + "\t" + image_features + " % " + image_id));
 
        }

    }
 
    public static class NodeRandomFilteringReducer extends Reducer<IntWritable, Text, Text, Text> {
        public void reduce(IntWritable key, Iterable<Text> values, Context context) throws IOException, InterruptedException {

            int selected_num = 0;
            int total = Integer.parseInt(context.getConfiguration().get("data_num")); 
            double ratio = Double.parseDouble(context.getConfiguration().get("ratio"));

            for (Text val: values) {
                if ((++selected_num / (float) total) > ratio) 
                    break;
                StringTokenizer tokenizer = tokenize(val, "\t"); 
                tokenizer.nextToken();
                tokenizer = tokenize(tokenizer.nextToken(), " %");
                context.write(new Text(tokenizer.nextToken()), new Text(tokenizer.nextToken()));
            }
        }
    }
 
    private static void setJobConfCompressed(Configuration jobconf) {
        jobconf.setBoolean("mapred.output.compress", true);
        jobconf.setClass("mapred.output.compression.codec", GzipCodec.class, CompressionCodec.class);
    }


    public static void main(String[] args) throws Exception {

        if (args.length < 2) {
            System.out.println("Usage: NodeRandomFiltering <input path> <selection ratio>");    
            System.exit(0);
        }

        int data_num = countingDataMapReduceJob(args[0]);
        System.out.println("The number of data: " + data_num);
        filteringMapReduceJob(args[0], data_num, Double.parseDouble(args[1]));

    }
 

    public static int countingDataMapReduceJob(String input_path) throws Exception {

        Configuration conf = new Configuration();
        conf.setLong("dfs.block.size",134217728);
        conf.set("mapred.child.java.opts", "-Xmx2048m");
        conf.set("path", "output/filtered_features/count");

        setJobConfCompressed(conf);        

        Job job = new Job(conf);
        job.setJarByClass(NodeRandomFiltering.class);
        job.setJobName("Counting Data");

        FileInputFormat.setInputPaths(job, new Path(input_path + "/*"));
        FileOutputFormat.setOutputPath(job, new Path("output/filtered_features/counting"));

        job.setOutputKeyClass(IntWritable.class);
        job.setOutputValueClass(IntWritable.class);
        job.setMapOutputKeyClass(IntWritable.class);
        job.setMapOutputValueClass(IntWritable.class);
        job.setMapperClass(NodeCountingMapper.class);
        job.setReducerClass(NodeCountingReducer.class);
        job.setNumReduceTasks(1);

        int number_of_data = -1;
 
        try {
            job.waitForCompletion(true);

            FileSystem fs;
            fs = FileSystem.get(conf);

            Path path_data_number_output = new Path("output/filtered_features/count");
            if(fs.exists(path_data_number_output)) {
              DataInputStream in = fs.open(path_data_number_output);
              number_of_data = in.readInt();
              in.close();
            }
        } catch(Exception e){
            e.printStackTrace();
        }

        return number_of_data;

    }   

    public static void filteringMapReduceJob(String input_path, int data_number, double ratio) throws Exception {

        Configuration conf = new Configuration();
        conf.setLong("dfs.block.size",134217728);
        conf.set("mapred.child.java.opts", "-Xmx2048m");
        conf.set("data_num", new Integer(data_number).toString());
        conf.set("ratio", new Double(ratio).toString());

        setJobConfCompressed(conf);        

        Job job = new Job(conf);
        job.setJarByClass(NodeRandomFiltering.class);
        job.setJobName("Filtering Nodes");

        FileInputFormat.setInputPaths(job, new Path(input_path + "/*"));
        FileOutputFormat.setOutputPath(job, new Path("output/filtered_features/filtered"));

        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);
        job.setMapOutputKeyClass(IntWritable.class);
        job.setMapOutputValueClass(Text.class);
        job.setMapperClass(NodeRandomFilteringMapper.class);
        job.setReducerClass(NodeRandomFilteringReducer.class);
        job.setNumReduceTasks(1);
 
        try {
            job.waitForCompletion(true);
        } catch(Exception e){
            e.printStackTrace();
        }
    }
}


