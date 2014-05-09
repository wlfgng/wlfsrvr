import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class TimeoutManager{


	public AtomicBoolean bool;
	private AtomicInteger latch;
	private int TIMEOUT;

	public TimeoutManager(int t, AtomicBoolean b){
		this.TIMEOUT = t;
		this.bool = b;
		latch = new AtomicInteger(0);
		bool.set(false);
	}


	public void resetTimeout(){
		//Increment the latch
		latch.incrementAndGet();
		System.out.println("Timeout reset " + latch.get());
		
		bool.set(false);
		
		//Start a new thread 
		new Thread(){
			public void run(){
				try{
					//Sleep for timeout
					Thread.sleep(TIMEOUT);
					//Decrement the latch
					latch.decrementAndGet();
					//If latch is zero, timeout has occured
					if(latch.get() == 0){
						bool.set(true);
						System.out.println("Timed out");
					}
				}catch(InterruptedException ie){
					ie.printStackTrace();
				}
			}
		}.start();
	}
}
