package dml.runtime.controlprogram.parfor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

import dml.runtime.controlprogram.ParForProgramBlock;
import dml.runtime.controlprogram.ProgramBlock;
import dml.runtime.controlprogram.parfor.stat.Stat;
import dml.runtime.controlprogram.parfor.stat.StatisticMonitor;
import dml.runtime.controlprogram.parfor.stat.Timing;
import dml.runtime.instructions.CPInstructions.Data;
import dml.runtime.instructions.CPInstructions.IntObject;
import dml.sql.sqlcontrolprogram.ExecutionContext;
import dml.utils.DMLRuntimeException;
import dml.utils.DMLUnsupportedOperationException;

/**
 * Super class for master/worker pattern implementations. Central place to
 * execute set or range tasks.
 * 
 * @author mboehm
 */
public abstract class ParWorker
{
	protected long                      _workerID    = -1;
	
	protected ArrayList<ProgramBlock>   _childBlocks = null;
	protected HashMap<String, Data>     _variables   = null;
	protected ExecutionContext     		_ec          = null;
	
	protected int                       _numTasks    = -1;
	protected int                       _numIters    = -1;
	
	public ParWorker()
	{
		//implicit constructor (required if parameters not known on object creation, 
		//e.g., RemoteParWorkerMapper)
	}
	
	public ParWorker( long ID, ParForBody body )
	{
		_workerID    = ID;
		
		if( body != null )
		{
			_childBlocks = body.getChildBlocks();
			_variables   = body.getVariables();
			_ec          = body.getEc();
		}
		
		_numTasks    = 0;
		_numIters    = 0;
	}
	
	/**
	 * 
	 * @return
	 */
	public HashMap<String,Data> getVariables()
	{
		return _variables;
	}
	
	/**
	 * Returns a summary statistic of executed tasks and hence should only be called 
	 * after execution.
	 * 
	 * @return
	 */
	public int getExecutedTasks()
	{
		return _numTasks;
	}
	
	/**
	 * Returns a summary statistic of executed iterations and hence should only be called 
	 * after execution.
	 * 
	 * @return
	 */
	public int getExecutedIterations()
	{
		return _numIters;
	}

	/**
	 * 
	 * @param task
	 * @throws DMLRuntimeException
	 * @throws DMLUnsupportedOperationException
	 */
	protected void executeTask( Task task ) 
		throws DMLRuntimeException, DMLUnsupportedOperationException 
	{
		switch( task.getType() )
		{
			case ITERATION_SET:
				executeSetTask( task );
				break;
			case ITERATION_RANGE:
				executeRangeTask( task );
				break;		
			//default: //MB: due to enum types this can never happen
			//	throw new DMLRuntimeException("Undefined task type: '"+task.getType()+"'.");
		}
	}
		
		
	/**
	 * 
	 * @param task
	 * @throws DMLRuntimeException
	 * @throws DMLUnsupportedOperationException
	 */
	private void executeSetTask( Task task ) 
		throws DMLRuntimeException, DMLUnsupportedOperationException 
	{
		//System.out.println(task.toCompactString());
		
		//monitoring start
		Timing time1, time2;		
		if( ParForProgramBlock.MONITOR )
		{
			time1 = new Timing(); time1.start();
			time2 = new Timing(); time2.start();
		}
		
		//core execution

		//foreach iteration in task, execute iteration body
		for( IntObject indexVal : task.getIterations() )
		{
			//set index values
			_variables.put(indexVal.getName(), indexVal);
			
			// for each program block
			for (ProgramBlock pb : _childBlocks)
			{	
				pb.setVariables(_variables);
				pb.execute(_ec);

				//update symbol table
				_variables = pb.getVariables();
			}
					
			_numIters++;
			
			if(ParForProgramBlock.MONITOR)
				StatisticMonitor.putPWStat(_workerID, Stat.PARWRK_ITER_T, time1.stop());
		}

		_numTasks++;
		
		//monitoring end
		if(ParForProgramBlock.MONITOR)
		{
			StatisticMonitor.putPWStat(_workerID, Stat.PARWRK_TASKSIZE, task.size());
			StatisticMonitor.putPWStat(_workerID, Stat.PARWRK_TASK_T, time2.stop());
		}
	}
	
	/**
	 * 
	 * @param task
	 * @throws DMLRuntimeException
	 * @throws DMLUnsupportedOperationException
	 */
	private void executeRangeTask( Task task ) 
		throws DMLRuntimeException, DMLUnsupportedOperationException 
	{
		//monitoring start
		Timing time1, time2;		
		if( ParForProgramBlock.MONITOR )
		{
			time1 = new Timing(); time1.start();
			time2 = new Timing(); time2.start();
		}
		
		//core execution
		LinkedList<IntObject> tmp = task.getIterations();
		String lVarName = tmp.get(0).getName();
		int lFrom       = tmp.get(0).getIntValue();
		int lTo         = tmp.get(1).getIntValue();
		int lIncr       = tmp.get(2).getIntValue();
		
		for( int i=lFrom; i<=lTo; i+=lIncr )
		{
			//set index values
			_variables.put(lVarName, new IntObject(lVarName,i)); 
			
			// for each program block
			for (ProgramBlock pb : _childBlocks)
			{	
				pb.setVariables(_variables);
				pb.execute(_ec);

				//update symbol table
				_variables = pb.getVariables();
			}
					
			_numIters++;
			
			if(ParForProgramBlock.MONITOR)
				StatisticMonitor.putPWStat(_workerID, Stat.PARWRK_ITER_T, time1.stop());	
		}

		_numTasks++;
		
		//monitoring end
		if(ParForProgramBlock.MONITOR)
		{
			StatisticMonitor.putPWStat(_workerID, Stat.PARWRK_TASKSIZE, task.size());
			StatisticMonitor.putPWStat(_workerID, Stat.PARWRK_TASK_T, time2.stop());
		}
	}
	
}

	
