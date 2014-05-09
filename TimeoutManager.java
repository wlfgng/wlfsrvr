public class TimeoutManager{


	public AtomicBoolean bool;
	private AtomicInteger latch;
	private int TIMEOUT;

	public TimeoutBoolean(int t){
		this.TIMEOUT = t;
		latch.set(0);
		bool.set(false);
	}


	public void resetTimeout(){
		//Increment the latch
		latch.incrementAndGet();
		
		
		//Start a new thread 
		new Thread(){
			public void run(){
				try{
					//Sleep for timeout
					thread.sleep(TIMEOUT);
					//Decrement the latch
					latch.decrementAndGet();
					//If latch is zero, timeout has occured
					if(latch.get() == 0)
						bool.set(true);
				}catch(InterruptedException ie){
					ie.printStackTrace();
				}
			}
		}
	}
}
