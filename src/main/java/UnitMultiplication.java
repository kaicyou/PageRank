import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.chain.ChainMapper;
import org.apache.hadoop.mapreduce.lib.input.MultipleInputs;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class UnitMultiplication {

    public static class TransitionMapper extends Mapper<Object, Text, Text, Text> {

        @Override
        public void map(Object key, Text value, Context context) throws IOException, InterruptedException {

            //input format: fromPage\t toPage1,toPage2,toPage3
            //target: build transition matrix unit -> fromPage\t toPage=probability
            String line = value.toString().trim();
            String[] fromTo = line.split("\t");
            
            // bad import
            if (fromTo.length < 2 || fromTo[1].trim().equals("")) {
                return;
            }

            String from = fromTo[0];
            String[] tos = fromTo[1].split(",");
            for (String toStr : tos) {
                context.write(new Text(from), new Text(toStr + "=" + (double)1 / tos.length));
            }
        }
    }

    public static class PRMapper extends Mapper<Object, Text, Text, Text> {

        @Override
        public void map(Object key, Text value, Context context) throws IOException, InterruptedException {

            //input format: Page\t PageRank
            //target: write to reducer
            String[] pr = value.toString().trim().split("\t");
            context.write(new Text(pr[0]), new Text(pr[1]));
        }
    }

    public static class MultiplicationReducer extends Reducer<Text, Text, Text, Text> {
        private float beta;

        @Override
        public void setup(Context context) {
            Configuration config = context.getConfiguration();
            beta = config.getFloat("beta", 0.2f);
        }


        @Override
        public void reduce(Text key, Iterable<Text> values, Context context)
                throws IOException, InterruptedException {

            //input key = fromPage value=<toPage=probability..., pageRank>
            //target: get the unit multiplication
            List<String> transitonCell = new ArrayList<>();
            double prCell = 0;
            for (Text val : values) {
                String valStr = val.toString();
                if (valStr.contains("=")) {
                    transitonCell.add(valStr);
                } else {
                    prCell = Double.parseDouble(valStr);
                }
            }
            for (String cell : transitonCell) {
                String outputKey = cell.split("=")[0];
                double prob = Double.parseDouble(cell.split("=")[1]);
                String outputVal = String.valueOf(prob * prCell * (1 - beta));
                context.write(new Text(outputKey), new Text(outputVal));
            }
        }
    }

    public static void main(String[] args) throws Exception {

        Configuration conf = new Configuration();
        conf.setFloat("beta", Float.parseFloat(args[3]));
        Job job = Job.getInstance(conf);
        job.setJarByClass(UnitMultiplication.class);

        //how chain two mapper classes?
        ChainMapper.addMapper(job, TransitionMapper.class, Object.class, Text.class, Text.class, Text.class, conf);
        ChainMapper.addMapper(job, PRMapper.class, Object.class, Text.class, Text.class, Text.class, conf);

        job.setReducerClass(MultiplicationReducer.class);

        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);

        MultipleInputs.addInputPath(job, new Path(args[0]), TextInputFormat.class, TransitionMapper.class);
        MultipleInputs.addInputPath(job, new Path(args[1]), TextInputFormat.class, PRMapper.class);

        FileOutputFormat.setOutputPath(job, new Path(args[2]));
        job.waitForCompletion(true);
    }

}
