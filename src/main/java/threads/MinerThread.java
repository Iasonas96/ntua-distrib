package threads;

import beans.Block;
import beans.Message;
import beans.MessageType;
import entities.Blockchain;

import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

/*
 * Class that represents a miner.
 */
public class MinerThread extends Thread{

    private static final Logger LOGGER = Logger.getLogger("NOOBCASH");
    private BlockingQueue<Message> inQueue;
    private BlockingQueue<Message> outQueue;
    private boolean mineInProgress;
    // Main thread's view about whether or not mining is in progress
    private int difficulty;

    public MinerThread(BlockingQueue<Message> queue, int difficulty) {
        this.outQueue = queue;
        inQueue = new LinkedBlockingQueue<>();
        this.difficulty = difficulty;
        mineInProgress = false;
    }

    @SuppressWarnings("InfiniteLoopStatement")
    @Override
    public void run() {

        Message msg;
        Block block;
        Random randomStream = new Random();
        while(true) {
            LOGGER.info("Waiting block to mine");
            try {
                msg = inQueue.take();
            } catch (InterruptedException e) {
                LOGGER.warning("Miner got interrupted while take'ing block to mine");
                continue;
            }
            if (msg.messageType != MessageType.BlockToMine) {
                LOGGER.severe("Instead of BlocToMine, got : " + msg.messageType);
                continue;
            }
            block = ((Block) msg.data);
            LOGGER.finer("Miner got block to mine : " + block.getIndex());

            // Check if interrupted, clearing interrupt flag if it is set
            while (!interrupted()){
                if (block.tryMine(randomStream.nextInt(), difficulty)) {
                    LOGGER.info("Block mined !");
                    try {
                        outQueue.put(new Message(MessageType.BlockMined, block));
                    } catch (InterruptedException e) {
                        LOGGER.warning("Miner interrupted while put'ing mined block");
                    }
                    break;
                }
            }
        }
    }

    /*
     * Interrupt miner, aborting possible mining operation
     * Called when new block is found or chain is replaced
     */
    public void stopMining() {
        mineInProgress = false;
        interrupt();
        // Don't trust flag, interrupt thread anyway (no harm done)
    }

    /*
     * Send a block to miner, if necessary
     */
    public void maybeMine(Blockchain blockchain){
        if (mineInProgress || !blockchain.isFull()) return;
        try {
            LOGGER.info("Put'ing block to be mined");
            mineInProgress = true;
            inQueue.put(new Message(MessageType.BlockToMine, blockchain.createBlock()));
        } catch (InterruptedException e) {
            LOGGER.severe("Unexpectedly interrupted while put'ing block to be mined");
        }
    }

    /*
     * Called from main thread when
     * BlockMined message is seen
     */
    public void blockMinedAck() {
        mineInProgress = false;
    }
}
