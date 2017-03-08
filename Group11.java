package examplepackage;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import negotiator.AgentID;
import negotiator.Bid;
import negotiator.Deadline;
import negotiator.actions.Accept;
import negotiator.actions.Action;
import negotiator.actions.Offer;
import negotiator.bidding.BidDetails;
import negotiator.boaframework.SortedOutcomeSpace;
import negotiator.parties.AbstractNegotiationParty;
import negotiator.session.TimeLineInfo;
import negotiator.utility.AbstractUtilitySpace;

public class Group11 extends AbstractNegotiationParty {
	
	/* proposedUtility is agent's utility for it's current bid
	 lastUtility is last utility of agent on last Offer made (includes agent's Offer also)
	 propUtilities contains all utilites above reservation value => proposable Utilities
	 selfUtilites contains all the bidable utilities for the agent
	 acceptedUtil contains record of player accepting the bid corresponding some utility (agent's)
	 */
	private double proposedUtility = 1.0, lastUtility = 0.0, rounds, reservationValue, discountFactor, hotRatio = 0.67, dealUtility = 0.9;
	private List<Double> propUtilities = new ArrayList<Double>();
	private List<Double> selfUtilities = new ArrayList<Double>();
	private boolean strategyMode = false, acceptedUtil[][], agentOrderMode = true, roundType = false;
	//hotIndex serves as another proposeIndex in strategyMode if we find a Bid being accepted by all in the acceptedUtil
	private int agentId, proposeIndex = 0, hotIndex = -1;
	private double globalCounter, localCounter, threshDiff, changeStamp = 0.0, timeEndThresh = 0.02;
	private List<BidDetails> outcomeSpace;
	private List<Bid> bidSpace = new ArrayList<Bid>();
	private Bid lastBid;
	private List<Double> hotSelect = new ArrayList<Double>();
	private HashMap<String, Integer> agentOrder = new HashMap<String, Integer>();
	
	@Override
	public void init(AbstractUtilitySpace utilSpace, Deadline dl, TimeLineInfo tl, long randomSeed, AgentID agentId) {
		super.init(utilSpace, dl, tl, randomSeed, agentId);
		// utilSpace and utilitySpace contain the same values
		// utilSpace contains value of perferences and values (just ours)
		// outcomeSpace contains all the outcomes
		outcomeSpace = new SortedOutcomeSpace(this.utilitySpace).getAllOutcomes();
		if(this.deadlines.getType().toString() == "ROUND")
		{
			roundType = true;
			System.out.println("Deadline type is : ROUND");
		}
		else
			System.out.println("Deadline type is : TIME");
			
	}
	
	/*
	 * Issue weight 0.25
		Values price: {50000yen=0.60, 60000yen=1.00, 55000yen=0.80, 40000yen=0.20, 45000yen=0.40}
		Issue weight 0.25
		Values WaterHeaterType: {Electric=1.00, Gas=0.50}
		Issue weight 0.25
		Values AcceptableLocations: {NearToGokisoStation=0.67, NearToFukiageStation=0.33, NearToTsrumaiStation=1.00}
		Issue weight 0.25
		Values Style: {Japanese=1.00, Western=0.50}

	 */
	
	public void setParams()
	{
		reservationValue = this.utilitySpace.getReservationValueUndiscounted();
		discountFactor = this.utilitySpace.getDiscountFactor();
		acceptedUtil = new boolean[this.getNumberOfParties()][outcomeSpace.size()];
		for (int i = 0; i < outcomeSpace.size(); i++)
		{
			// making acceptedUtil of self always equal to 1
			selfUtilities.add(outcomeSpace.get(i).getMyUndiscountedUtil());
			bidSpace.add(this.outcomeSpace.get(i).getBid());
			hotSelect.add(1.0);
			if(outcomeSpace.get(i).getMyUndiscountedUtil() <= reservationValue)
				continue;
			propUtilities.add(outcomeSpace.get(i).getMyUndiscountedUtil());
		}
		rounds = this.deadlines.getValue();
		globalCounter = rounds/2;
		localCounter = globalCounter;
		if(roundType)
			threshDiff = (Math.sqrt(rounds));
		else
		{
			//TODO make this metric domain independent
			// need less equal threshDiff seconds to roll down the rest of the utility space
			if(this.deadlines.getValue() > 10)
				threshDiff = 0.25 * this.getNumberOfParties();
			else
				threshDiff = 0.15 * this.getNumberOfParties();
		}
		if (discountFactor != 1.0)
			dealUtility = Math.max(dealUtility, selfUtilities.get((int)(selfUtilities.size()/2)));	
		else
			dealUtility = 1.0;
	}
	
	public void changeProposal()
	{
		// SortedOutcomeSpace sorts in descending order therfore we start with zero index and increase index as time goes on..
		// proposing next preferred bid
		proposeIndex++;
		if(proposeIndex >= propUtilities.size())
			proposeIndex--;
		return;
	}
	
	public void checkCounter()
	{
		if(strategyMode)
		{
			boolean acceptance = true;
			// this is always bounded by proposeIndex which will never let our proposal go beyond reservation value
			for (int j = hotIndex + 1; j <= proposeIndex; j++) 
			{
				acceptance = true;
				for (int i = 0; i < this.getNumberOfParties(); i++) 
				{
					if(acceptedUtil[i][j] == false)
						acceptance = false;
				}
				// Here we discover whether acceptance is true or not.
				if(acceptance && Math.random() <= hotSelect.get(j))
				{
					// If player don't fit into the model of rationality assumed then we might be stuck
					// If it fails we select that with less probability.
					hotIndex = j;
					hotSelect.set(j, hotSelect.get(j) * hotRatio);
					break;
				}
				else
					acceptance = false;
			}
			if(!acceptance)
			{
				changeProposal();
				hotIndex = -1;
			}	
		}
		else
		{
			if(roundType)
				localCounter--;
			else
				localCounter = globalCounter - this.timeline.getCurrentTime() + changeStamp;
			if(localCounter <= 0)
			{
				if(!roundType)
					changeStamp = this.timeline.getCurrentTime();
				globalCounter /= 2;
				localCounter = globalCounter;
				if(localCounter < threshDiff)
					strategyMode = true;
				changeProposal();
			}
		}
		proposedUtility = propUtilities.get(proposeIndex);
		if(roundType)
			rounds--;
		else
			rounds = this.timeline.getTotalTime() - this.timeline.getCurrentTime();
	}
	
	@Override
	public Action chooseAction(List<Class<? extends Action>> possibleActions) {
		if(agentOrderMode)
		{
			parseId(this.getPartyId());
			for (int i = 0; i < outcomeSpace.size(); i++) {
				acceptedUtil[parseId(this.getPartyId())][i] = true;
			}
		}
		checkCounter();
		if(rounds < timeEndThresh && parseId(this.getPartyId()) != 0 && lastUtility > reservationValue)
			return new Accept();
		else if(lastUtility < proposedUtility && lastUtility < dealUtility)
		{
			lastBid = generateBid();
			lastUtility = getUtility(lastBid);
			return new Offer(lastBid);
		}
		else{
			return new Accept();
		}
	}
	
	public Bid generateBid()
	{
		if(strategyMode && hotIndex >= 0)
			return this.outcomeSpace.get(hotIndex).getBid();
		return this.outcomeSpace.get(proposeIndex).getBid();
	}
	
	@Override
	public void receiveMessage(AgentID sender, Action arguments) {
		super.receiveMessage(sender, arguments);
		boolean flag = false;
		if(sender != null)
		{
			if(agentOrderMode)
				parseId(sender);
			if(arguments != null)
				flag = true;
		}
		else
		{
			/* A message is received sent by system which has sender == null and arguments = null
			 * This message updates some stuff like this.getNumberOfParties()
			 */
			setParams();
		}
		if(flag)
		{

			try{
				agentId = parseId(sender);
//				If any agent accepts, a utility value of 0 is returned.
				if(getUtility(Action.getBidFromAction(arguments)) != 0)
				{
					lastBid = Action.getBidFromAction(arguments);
					lastUtility = getUtility(lastBid);
				}
				// If the agent accepts, technically it accepts lastBid made in the last offer so making it's value true
				//	Formulation below is accurate as there can exist multiple Bid with same utilites for us.
				acceptedUtil[agentId][this.bidSpace.indexOf(lastBid)] = true;
			}
			catch(Exception e)
			{
				System.out.println("Invalid message received");
			}
		}
	}
	
	public int parseId(AgentID agentId)
	{
		// Agent has usually some string attached to it followed by the number representing an integer ID
		// return agentID - 1 for index purposes
		if(!agentOrder.containsKey(agentId.toString()))
			agentOrder.put(agentId.toString(), agentOrder.size());
		else
			agentOrderMode = false;
		return agentOrder.get(agentId.toString());
		// String temp = agentId.toString().replaceAll("[^0-9]+", " ");
		// return (Integer.parseInt(Arrays.asList(temp.trim().split(" ")).get(0)) - 1);
	}
	
	@Override
	public AgentID getPartyId() {
		return super.getPartyId();
	}
}
