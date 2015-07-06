/*
 *
 *  * Copyright 2015 Skymind,Inc.
 *  *
 *  *    Licensed under the Apache License, Version 2.0 (the "License");
 *  *    you may not use this file except in compliance with the License.
 *  *    You may obtain a copy of the License at
 *  *
 *  *        http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *    Unless required by applicable law or agreed to in writing, software
 *  *    distributed under the License is distributed on an "AS IS" BASIS,
 *  *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *    See the License for the specific language governing permissions and
 *  *    limitations under the License.
 *
 */

package org.deeplearning4j.cli.subcommands;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.canova.api.formats.input.InputFormat;
import org.canova.api.records.reader.RecordReader;
import org.canova.api.split.FileSplit;
import org.canova.api.split.InputSplit;
import org.deeplearning4j.cli.conf.ModelConfigurationUtil;
import org.deeplearning4j.datasets.canova.RecordReaderDataSetIterator;
import org.deeplearning4j.nn.api.Layer;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.optimize.api.IterationListener;
import org.deeplearning4j.optimize.listeners.ScoreIterationListener;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.nd4j.linalg.dataset.api.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;

import org.deeplearning4j.nn.layers.OutputLayer;
import org.deeplearning4j.nn.layers.factory.LayerFactories;
import org.deeplearning4j.nn.api.LayerFactory;
import org.nd4j.linalg.dataset.SplitTestAndTrain;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Subcommand for training model
 *
 * Options:
 *      Required:
 *          -input: input data file for model
 *          -model: json configuration for model
 *
 * @author sonali
 */
public class Train implements SubCommand {


    public static final String EXECUTION_RUNTIME_MODE_KEY = "dl4j.execution.runtime";
    public static final String EXECUTION_RUNTIME_MODE_DEFAULT = "local";

    public static final String OUTPUT_FILENAME_KEY = "dl4j.output.directory";
    public static final String INPUT_DATA_FILENAME_KEY = "dl4j.input.directory";

    public static final String INPUT_FORMAT_KEY = "dl4j.input.format";
    public static final String DEFAULT_INPUT_FORMAT_CLASSNAME = "org.canova.api.formats.input.impl.SVMLightInputFormat";

    // the parameters of the actual model
    public static final String MODEL_CONFIG_KEY = "dl4j.model.config";
    public static final String MODEL_CONFIG_VALUE_DEFAULT = ""; // needs to auto-gen then


    @Option(name = "-conf", usage = "configuration file for training" )
    public String configurationFile = "";

    public boolean validCommandLineParameters = false;
    public boolean validModelConfigJSONFile = false;
    public boolean usingDefaultModelConfigJSONFile = false;
    
    public Properties configProps = null;

    private static Logger log = LoggerFactory.getLogger(Train.class);


    // NOTE: disabled this setup for now for development purposes

    @Option(name = "-input", usage = "input data",aliases = "-i", required = false)
    private String input = "input.txt";


    @Option(name = "-output", usage = "location for saving model", aliases = "-o")
    private String outputDirectory = "output.txt";
//    @Option(name = "-model",usage = "location for configuration of model",aliases = "-m")
//    private String modelPath;
    @Option(name = "-type",usage = "type of network (layer or multi layer)")
    private String type = "multi";

    @Option(name = "-runtime", usage = "runtime- local, Hadoop, Spark, etc.", aliases = "-r", required = false)
    private String runtime = "local";

    @Option(name = "-properties", usage = "configuration for distributed systems", aliases = "-p", required = false)
    private String properties;
    @Option(name = "-savemode",usage = "output: (binary | txt)")
    private String saveMode = "txt";
    @Option(name = "-verbose",usage = "verbose(true | false)",aliases  = "-v")
    private boolean verbose = false;

    // the json file that describes the model
    private String jsonModelConfigPath = "";
    protected String[] args;

    public Train() {
    //    this(new String[1]);
    }

    public Train(String[] args) {
        //super(args);
        /*
        this.args = args;
        CmdLineParser parser = new CmdLineParser(this);
        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            this.validCommandLineParameters = false;
            parser.printUsage(System.err);
            log.error("Unable to parse args", e);
        }
        */
    	
        this.args = args;
        CmdLineParser parser = new CmdLineParser(this);
        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            this.validCommandLineParameters = false;
            parser.printUsage(System.err);
            log.error("Unable to parse args", e);
        }
    	
    	
    }
    
    public void debugPrintConf() {
    	
    	System.out.println( "DL4J: Deep Learning Engine Command-Line Interface > Debug Print Conf ----" );
    	
    	System.out.println("-----------------------------");
        Properties props = this.configProps; //System.getProperties();
        Enumeration e = props.propertyNames();

        while (e.hasMoreElements()) {
            String key = (String) e.nextElement();
            System.out.println(key + " -- " + props.getProperty(key));
        }

        System.out.println("-----------------------------\n");
    }
    
    
    public static void printUsage() {
    	
    	System.out.println( "DL4J: CLI Training System" );
    	System.out.println( "" );
    	System.out.println( "\tUsage:" );
    	System.out.println( "\t\tdl4j train -conf <conf_file>" );
    	System.out.println( "" );
    	System.out.println( "\tConfiguration File:" );
    	System.out.println( "\t\tContains a list of property entries that describe the training process" );
    	System.out.println( "" );
    	System.out.println( "\tExample:" );
    	System.out.println( "\t\tdl4j train -conf /tmp/iris_conf.txt " );
    	
    	
    }
    

    /**
     * TODO:
     * 		-	lots of things to do here
     * 		-	runtime: if we're running on a cluster, then we have a different workflow / tracking setup
     *
     *
     */
    @Override
    public void execute() {
    	
    	
    	if ("".equals(this.configurationFile)) {
    		printUsage();
    		return;
    	}

    
        try {
            loadConfigFile();
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // now check on the model
        
        this.validateModelConfigFile();
        
        if (!this.validModelConfigJSONFile) {
        	System.out.println( "Shutting down, no valid model JSON architecture file specified: " + this.jsonModelConfigPath );
        	return;
        }
        

        if ("hadoop".equals(this.runtime.trim().toLowerCase())) {
        	
            this.execOnHadoop();

        } else if ("spark".equals(this.runtime.trim().toLowerCase())) {
        	
            this.execOnSpark();

        } else {

            this.execLocal();
            
        }



    }

    /**
     * Execute local training
     */
    public void execLocal() {
        
    	log.info( "Executing local ... " );
        log.info( "Using training configuration: " + this.configurationFile );

        File inputFile = new File( this.input );
        InputSplit split = new FileSplit( inputFile );
        InputFormat inputFormat = this.createInputFormat();

        RecordReader reader = null;

        try {
            reader = inputFormat.createReader(split);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if(type.equals("multi")) {
            try {
                MultiLayerConfiguration conf = MultiLayerConfiguration.fromJson(FileUtils.readFileToString(new File( this.jsonModelConfigPath )));
                DataSetIterator iter = new RecordReaderDataSetIterator( reader , conf.getConf(0).getBatchSize(),-1,conf.getConf(conf.getConfs().size() - 1).getNOut());

                MultiLayerNetwork network = new MultiLayerNetwork(conf);
                if(verbose) {
                    network.init();
                    network.setListeners(Collections.<IterationListener>singletonList(new ScoreIterationListener(1)));
                }
                network.fit(iter);
                if(saveMode.equals("binary")) {
                    BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(this.outputDirectory + File.separator + "outputmodel.bin"));
                    DataOutputStream dos = new DataOutputStream(bos);
                    Nd4j.write(network.params(),dos);
                }
                else {
                    Nd4j.writeTxt(network.params(),outputDirectory + File.separator + "outputmodel.txt",",");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        else {
            try {
                NeuralNetConfiguration conf = NeuralNetConfiguration.fromJson(FileUtils.readFileToString(new File( this.jsonModelConfigPath )));
                LayerFactory factory = LayerFactories.getFactory(conf);
                Layer l = factory.create(conf);
                DataSetIterator iter = new RecordReaderDataSetIterator( reader , conf.getBatchSize());
                while(iter.hasNext()) {
                    l.fit(iter.next().getFeatureMatrix());
                }

                if(saveMode.equals("binary")) {
                    BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(this.outputDirectory));
                    DataOutputStream dos = new DataOutputStream(bos);
                    Nd4j.write(l.params(),dos);
                }
                else {
                    Nd4j.writeTxt(l.params(),outputDirectory,",");
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }


    }

    public void execOnSpark() {
        log.warn( "DL4J: Execution on spark from CLI not yet supported" );
    }

    public void execOnHadoop() {
        log.warn( "DL4J: Execution on hadoop from CLI not yet supported" );
    }

    /**
     * Create an input format
     * @return the input format to be created
     */
    public InputFormat createInputFormat() {
       if(configProps == null)
           try {
               loadConfigFile();
           } catch (Exception e) {
               e.printStackTrace();
           }
        //log.warn( "> Loading Input Format: " + (String) this.configProps.get( INPUT_FORMAT ) );

        String clazz = (String) this.configProps.get( INPUT_FORMAT_KEY );

        if ( null == clazz ) {
            clazz = DEFAULT_INPUT_FORMAT_CLASSNAME;
        }

        try {
            Class<? extends InputFormat> inputFormatClazz = (Class<? extends InputFormat>) Class.forName(clazz);
            return inputFormatClazz.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }
    
    /**
     * Generate a local default model config json file to start the network off with.
     * 
     */
    public void generateDefaultModelConfigFile() {
    	
    	
    }
    
    /**
     * Loads the model config JSON file
     * 
     * Note: this is separate from the training process configuration file by design (too hard to get all that JSON on one property line)
     * 
     * 
     */
    public void validateModelConfigFile() {
    	
    	// validate the model arch
    	
    	if (ModelConfigurationUtil.validateExistingJsonConfigFile( this.jsonModelConfigPath )) {
    		
    		System.out.println( "JSON Model Architecture is validated." );
    		this.validModelConfigJSONFile = true;
    		
    	} else {
    		
    		this.validModelConfigJSONFile = false;
    		
    	}
    	
    	
    }


    /**
     * Loads the training process config file
     * 
     * Configures things like:
     * 	-	input data path
     * 	-	output directory
     * 	-	model json config file path
     * 
     * @throws Exception
     */
    public void loadConfigFile() throws Exception {

        this.configProps = new Properties();
        
        
//System.out.println( "loading conf file: " + this.configurationFile );
        InputStream in = null;
        try {
            in = new FileInputStream( this.configurationFile );
        } catch (FileNotFoundException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        try {
            this.configProps.load(in);
            in.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }



        // get runtime - EXECUTION_RUNTIME_MODE_KEY
        if (this.configProps.get( EXECUTION_RUNTIME_MODE_KEY ) != null) {
            this.runtime = (String) this.configProps.get(EXECUTION_RUNTIME_MODE_KEY);

        } else {
            this.runtime = EXECUTION_RUNTIME_MODE_DEFAULT;
        }

        // get output directory
        if (null != this.configProps.get( OUTPUT_FILENAME_KEY )) {
        	
        
            this.outputDirectory = (String) this.configProps.get(OUTPUT_FILENAME_KEY);

        } else {
            // default
            this.outputDirectory = "/tmp/dl4_model_default.model";
        //throw new Exception("no output location!");
        }


        // get input data

        if ( null != this.configProps.get( INPUT_DATA_FILENAME_KEY )) {
        	
            this.input = (String) this.configProps.get(INPUT_DATA_FILENAME_KEY);

        } else {
            throw new RuntimeException("no input file to train on!");
        }

        // get MODEL_CONFIG_KEY
        
        if ( null != this.configProps.get(MODEL_CONFIG_KEY)) {
        	
        	this.jsonModelConfigPath = (String) this.configProps.getProperty(MODEL_CONFIG_KEY);
        	
        } else {
        	
        	// need to auto-gen the model config JSON file [ cold start problem ]
        	
        	System.out.println( "Warning: No model was defined, default parameters being used. Training will be sub-optimal." );
        	
        }
        
    }
}