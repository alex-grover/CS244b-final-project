package edu.stanford.cs244b.chord;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Stabilizer extends Thread {
	final static int SLEEP_MILLIS = 5000;
	
	final static Logger logger = LoggerFactory.getLogger(Stabilizer.class);
	
	final ChordNode node;
	
	public Stabilizer(ChordNode node) {
		this.node = node;
	}
	
	/** Run stabilization and fix fingers for ChordNode */
	public void run() {
		try {
			while (!Thread.currentThread().isInterrupted()) {
				node.stabilize();
				if (node.stable()) node.fixFingers();
				Thread.sleep(SLEEP_MILLIS);
			}
		} catch (InterruptedException e) {
			logger.info("Exiting...", e);
		}
	}
	
	/** Kill stabilization thread */
	public void cancel() {
		interrupt();
	}
}
