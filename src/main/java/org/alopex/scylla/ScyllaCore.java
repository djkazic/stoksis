package org.alopex.scylla;

import java.io.File;
import java.util.logging.LogManager;

import org.encog.engine.network.activation.ActivationTANH;
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
			network = new BasicNetwork();
			initNetworkArch();
		} else {
			System.out.println("Existing core detected. Loading...");
			System.out.println();
			network = (BasicNetwork) EncogDirectoryPersistence.loadObject(new File("core.dat"));
		}

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
		
		if(eval)
			mc.eval(network);
	}

	private static void initNetworkArch() {
		network.addLayer(new BasicLayer(null, true, Settings.inputs));
		for(int i = 0; i < Settings.hiddenLayers; i++) {
			network.addLayer(new BasicLayer(new ActivationTANH(), true, Settings.hiddens));
		}
		network.addLayer(new BasicLayer(new ActivationTANH(), false, Settings.outputs));
		network.getStructure().finalizeStructure();
		network.reset();
	}
}
