package org.alopex.scylla;

import java.io.File;
import java.util.logging.LogManager;

import org.encog.engine.network.activation.ActivationSigmoid;
import org.encog.neural.networks.BasicNetwork;
import org.encog.neural.networks.layers.BasicLayer;
import org.encog.persist.EncogDirectoryPersistence;

/**
 * @author Kevin Cai on 1/30/2016.
 */
public class ScyllaCore {

	private static BasicNetwork network;
	private static MinerController mc;

	public static void main(String[] args) {
		LogManager.getLogManager().reset();

		if(!new File("core.dat").exists()) {
			System.out.println("Starting new core...");
			network = new BasicNetwork();
			initNetworkArch();
		} else {
			System.out.println("Existing core detected. Loading...");
			network = (BasicNetwork) EncogDirectoryPersistence.loadObject(new File("core.dat"));
		}
		System.out.println();

		mc = new MinerController();
		
		boolean train = false;
		boolean eval = false;
		
		for(String str : args) {
			String lower = str.toLowerCase();
			
			switch(lower) {
				case "--train":
					train = true;
					break;
					
				case "--eval":
					eval = true;
					break;
			}
		}
		
		if(train)
			mc.train(network);
		
		if(eval) {
			for(int i = 10; i > 0; i--) {
				mc.eval(network, "GOOG", i);
			}
		}
	}

	private static void initNetworkArch() {
		network.addLayer(new BasicLayer(null, true, Settings.inputs));
		for(int i = 0; i < Settings.hiddenLayers; i++) {
			network.addLayer(new BasicLayer(new ActivationSigmoid(), true, Settings.hiddens));
		}
		network.addLayer(new BasicLayer(new ActivationSigmoid(), false, Settings.outputs));
		network.getStructure().finalizeStructure();
		network.reset();
	}
}
