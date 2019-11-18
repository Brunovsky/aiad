package agents;

import static agents.Technician.State.UNEMPLOYED;
import static agents.Technician.State.WORKING;
import static jade.lang.acl.MessageTemplate.MatchOntology;
import static jade.lang.acl.MessageTemplate.MatchPerformative;
import static jade.lang.acl.MessageTemplate.MatchSender;
import static jade.lang.acl.MessageTemplate.and;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import agentbehaviours.AwaitDay;
import agentbehaviours.AwaitNight;
import agentbehaviours.WorldLoop;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.SequentialBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import simulation.World;
import strategies.TechnicianStrategy;
import types.Contract;
import types.WorkFinance;
import types.WorkLog;
import utils.Logger;

public class Technician extends Agent {
    private static final long serialVersionUID = 2763283727137639385L;

    private final String id;
    private final AID homeStation;
    private AID company;

    private final Map<Integer, WorkLog> workHistory;
    private final ArrayList<Contract> contractHistory;
    private Contract currentContract, nextContract;
    private State state;

    private final TechnicianStrategy strategy;

    public enum State { WORKING, UNEMPLOYED }

    public Technician(String id, AID homeStation, AID company, TechnicianStrategy strategy) {
        assert id != null && homeStation != null && company != null && strategy != null;
        this.id = id;
        this.homeStation = homeStation;
        this.company = company;

        this.workHistory = new HashMap<>();
        this.contractHistory = new ArrayList<>();
        this.currentContract = null;
        this.nextContract = null;
        this.state = UNEMPLOYED;

        this.strategy = strategy;
        strategy.setTechnician(this);
    }

    @Override
    protected void setup() {
        Logger.info(id, "Setup " + id);

        // SETUP
        addBehaviour(new InitialEmployment(this));

        SequentialBehaviour sequential = new SequentialBehaviour(this);

        // NIGHT
        sequential.addSubBehaviour(new AwaitNight(this));
        sequential.addSubBehaviour(new TechnicianNight(this));

        // DAY
        sequential.addSubBehaviour(new AwaitDay(this));
        sequential.addSubBehaviour(new FindNextContract(this));
        sequential.addSubBehaviour(new MoveToNextContract(this));

        addBehaviour(new WorldLoop(sequential));
    }

    @Override
    protected void takeDown() {
        Logger.warn(id, "Technician Terminated!");
    }

    public AID getHomeStation() {
        return homeStation;
    }

    public AID getCompany() {
        return company;
    }

    public WorkLog getWorkLog(int day) {
        return workHistory.get(day);
    }

    public Contract getPreviousContract() {
        if (contractHistory.isEmpty()) return null;
        return contractHistory.get(contractHistory.size() - 1);
    }

    public Contract getCurrentContract() {
        return currentContract;
    }

    public Contract getNextContract() {
        return nextContract;
    }

    public State getWorkState() {
        return state;
    }

    // ***** DATA

    private void createWorkLog(WorkFinance finance) {
        int day = World.get().getDay();
        assert finance.salary == currentContract.salary;
        WorkLog log = new WorkLog(this, currentContract, finance);
        workHistory.put(day, log);
    }

    private void createEmptyWorkLog() {
        int day = World.get().getDay();
        WorkLog log = new WorkLog(this);
        workHistory.put(day, log);
    }

    // ***** BEHAVIOURS

    class FindNextContract extends OneShotBehaviour {
        private static final long serialVersionUID = 2433586834474062536L;

        FindNextContract(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            if (nextContract != null || !strategy.lookForContracts()) return;

            Contract renewed = strategy.renewalContract();

            ACLMessage message = new ACLMessage(ACLMessage.PROPOSE);
            message.setOntology(World.get().getTechnicianOfferContract());
            message.setContent(renewed.make());
            send(message);

            // TODO SIMPLIFICATION
            MessageTemplate onto, acl, mt;
            onto = MatchOntology(World.get().getTechnicianOfferContract());
            acl = MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
            mt = and(onto, acl);
            ACLMessage reply = receive(mt);
            while (reply == null) {
                block();
                reply = receive(mt);
            }

            contractHistory.add(renewed);
            nextContract = renewed;
        }
    }

    class MoveToNextContract extends OneShotBehaviour {
        private static final long serialVersionUID = -966288207328177898L;

        MoveToNextContract(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            int day = World.get().getDay();

            if (currentContract != null && day == currentContract.end) {
                contractHistory.add(currentContract);
                currentContract = null;
                state = UNEMPLOYED;
            }

            if (nextContract != null && day + 1 == nextContract.start) {
                currentContract = nextContract;
                nextContract = null;
                state = WORKING;
            }
        }
    }

    class TechnicianNight extends OneShotBehaviour {
        private static final long serialVersionUID = 3576074310971384343L;

        TechnicianNight(Agent a) {
            super(a);
        }

        private void unemployedAction() {
            createEmptyWorkLog();
        }

        private void workingAction() {
            MessageTemplate acl, onto, mt;

            onto = MatchOntology(World.get().getCompanyPayment());
            acl = MatchPerformative(ACLMessage.INFORM);
            mt = and(and(onto, acl), MatchSender(company));
            ACLMessage inform = receive(mt);  // Protocol A
            while (inform == null) {
                block();
                inform = receive(mt);
            }
            createWorkLog(WorkFinance.from(inform));
        }

        @Override
        public void action() {
            if (state == UNEMPLOYED) {
                unemployedAction();
            } else {
                workingAction();
            }
        }
    }

    private class InitialEmployment extends OneShotBehaviour {
        private static final long serialVersionUID = -8275421706452630634L;
        private String ontology;

        InitialEmployment(Agent a) {
            super(a);
        }

        @Override
        public void action() {
            ontology = World.get().getInitialEmployment();
            MessageTemplate onto, acl, mt;

            ACLMessage message = new ACLMessage(ACLMessage.SUBSCRIBE);
            message.setOntology(ontology);
            message.addReceiver(company);
            send(message);

            onto = MatchOntology(ontology);
            acl = MatchPerformative(ACLMessage.INFORM);
            mt = and(and(onto, acl), MatchSender(company));
            ACLMessage reply = receive(mt);
            while (reply == null) {
                block();
                reply = receive(mt);
            }

            Contract contract = Contract.from(company, myAgent.getAID(), reply);
            currentContract = contract;
            state = WORKING;

            Logger.info(id, "Initial employment @" + company.getLocalName());
        }
    }
}
