import React, { useState } from 'react';
import axios from 'axios';
import { Truck, Calculator, ArrowRight, RefreshCcw, LayoutGrid, XCircle, CheckCircle, Edit3, Settings } from 'lucide-react';

const App = () => {
  const [step, setStep] = useState(1);
  const [suppliers, setSuppliers] = useState('');
  const [customers, setCustomers] = useState('');
  const [costs, setCosts] = useState([]);
  const [supply, setSupply] = useState([]);
  const [demand, setDemand] = useState([]);
  const [result, setResult] = useState(null);
  const [totalCost, setTotalCost] = useState(0);
  const [method, setMethod] = useState('');
  const [vogelSteps, setVogelSteps] = useState([]);
  const [showVogelModal, setShowVogelModal] = useState(false);
  const [showUnbalancedModal, setShowUnbalancedModal] = useState(false);
  const [unbalancedInfo, setUnbalancedInfo] = useState({});

  const handleSetup = () => {
    if (!suppliers || !customers) return;
    const r = parseInt(suppliers);
    const c = parseInt(customers);
    setCosts(Array(r).fill('').map(() => Array(c).fill('')));
    setSupply(Array(r).fill(''));
    setDemand(Array(c).fill(''));
    setStep(2);
  };

  const handleCalcClick = (m) => {
    const sTotal = supply.reduce((a, b) => a + (parseFloat(b) || 0), 0);
    const dTotal = demand.reduce((a, b) => a + (parseFloat(b) || 0), 0);
    setMethod(m);

    if (sTotal !== dTotal) {
      setUnbalancedInfo({ diff: Math.abs(sTotal - dTotal), type: sTotal > dTotal ? "Dummy Warehouse" : "Dummy Factory", sTotal, dTotal });
      setShowUnbalancedModal(true);
    } else {
      runCalculation(m);
    }
  };

  const runCalculation = async (m = method) => {
    setShowUnbalancedModal(false);
    try {
      const response = await axios.post('http://localhost:8080/api/transportation/calculate', {
        suppliers: parseInt(suppliers), customers: parseInt(customers),
        costs: costs.map(r => r.map(v => v === '-' ? -1 : (parseFloat(v) || 0))),
        supply: supply.map(v => parseFloat(v) || 0),
        demand: demand.map(v => parseFloat(v) || 0),
        method: m
      });

      setResult(response.data.allocation);
      setTotalCost(response.data.totalCost);
      if (m === 'VOGEL') {
          setVogelSteps(response.data.steps || []); 
          setShowVogelModal(true);
      } else {
          setStep(3);
      }
    } catch (e) { alert("Backend Error! Is IntelliJ Running?"); }
  };

  const sTotal = supply.reduce((a, b) => a + (parseFloat(b) || 0), 0);
  const dTotal = demand.reduce((a, b) => a + (parseFloat(b) || 0), 0);

  return (
    <div className="min-vh-100 py-5 bg-gradient-to-br from-slate-50 to-blue-100 text-dark font-sans">
      <div className="container">
        <h1 className="text-center fw-black text-primary mb-5 display-4 italic">
            <Truck size={50} className="mb-2" /><br/>TRANSPORTATION SOLVER
        </h1>

        {/* STEP 1 */}
        {step === 1 && (
          <div className="card shadow-lg border-0 p-5 mx-auto rounded-4 bg-white" style={{maxWidth: '450px'}}>
            <h4 className="text-center mb-4 fw-bold text-uppercase tracking-wider">Step 1: Setup</h4>
            <label className="small fw-bold text-muted mb-1">NO. OF FACTORIES</label>
            <input type="number" className="form-control form-control-lg mb-3 shadow-sm border-2" value={suppliers} onChange={(e)=>setSuppliers(e.target.value)} placeholder="0" />
            <label className="small fw-bold text-muted mb-1">NO. OF WAREHOUSES</label>
            <input type="number" className="form-control form-control-lg mb-4 shadow-sm border-2" value={customers} onChange={(e)=>setCustomers(e.target.value)} placeholder="0" />
            <button onClick={handleSetup} className="btn btn-primary btn-lg w-100 shadow-sm py-3 fw-bold rounded-3">
                ENTER COST <ArrowRight className="ms-2" />
            </button>
          </div>
        )}

        {/* STEP 2 */}
        {step === 2 && (
          <div className="card shadow-lg border-0 p-4 rounded-4 bg-white animate-in slide-in-from-bottom-5">
            <h4 className="text-center mb-4 fw-bold">STEP 2: COST ANALYSIS GRID</h4>
            <div className="table-responsive">
              <table className="table table-bordered align-middle text-center">
                <thead className="bg-primary text-white">
                  <tr>
                    <th>UNIT COSTS</th>
                    {[...Array(parseInt(customers))].map((_, j) => <th key={j}>W{j+1}</th>)}
                    <th className="bg-silver text-blue">SUPPLY</th>
                  </tr>
                </thead>
                <tbody>
                  {costs.map((row, i) => (
                    <tr key={i}>
                      <td className="fw-bold bg-light">Factory {i+1}</td>
                      {row.map((val, j) => (
                        <td key={j} className="p-0">
                          <input type="text" className="form-control border-0 text-center fw-bold" value={val} placeholder="-" onChange={(e)=>{
                            const n = [...costs]; n[i][j] = e.target.value; setCosts(n);
                          }}/>
                        </td>
                      ))}
                      <td className="p-0 bg-blue-50">
                        <input type="number" className="form-control border-0 text-center fw-bold bg-transparent" value={supply[i]} placeholder="S" onChange={(e)=>{
                          const n = [...supply]; n[i] = e.target.value; setSupply(n);
                        }}/>
                      </td>
                    </tr>
                  ))}
                  <tr className="table-light">
                    <td className="fw-bold text-primary">DEMAND</td>
                    {demand.map((val, j) => (
                      <td key={j} className="p-0">
                        <input type="number" className="form-control border-0 text-center fw-bold bg-transparent" value={val} placeholder="D" onChange={(e)=>{
                          const n = [...demand]; n[j] = e.target.value; setDemand(n);
                        }}/>
                      </td>
                    ))}
                    <td className={`fw-bold py-2 ${sTotal !== dTotal ? 'text-danger animate-pulse' : 'text-success'}`}>
                        {sTotal === dTotal ? `BAL: ${sTotal}` : "UNBALANCED"}
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            
            <div className="mt-5 p-4 border rounded-4 bg-light text-center shadow-inner">
                <h5 className="fw-bold text-muted mb-4">CALCULATE IBFS USING:</h5>
                <div className="d-flex justify-content-center gap-3 flex-wrap">
                    <button onClick={()=>handleCalcClick('NW')} className="btn btn-dark px-4 py-2 fw-bold hover:opacity-90">North-West Corner</button>
                    <button onClick={()=>handleCalcClick('LEAST')} className="btn btn-primary px-4 py-2 fw-bold shadow-sm hover:bg-primary-dark">Least Cost Method</button>
                    <button onClick={()=>handleCalcClick('VOGEL')} className="btn btn-info text-white px-4 py-2 fw-bold shadow-sm">Vogel's Method (VAM)</button>
                </div>
            </div>
          </div>
        )}

        {/* STEP 3 */}
{step === 3 && result && (
  <div className="card shadow-lg border-0 p-5 rounded-4 bg-white animate-in zoom-in">
    <div className="row g-5">
      <div className="col-lg-7">
        <h4 className="fw-bold mb-4 text-primary d-flex align-items-center gap-2">
            <LayoutGrid size={24}/> FINAL ALLOCATION MATRIX
        </h4>
        <div className="table-responsive border rounded-3 overflow-hidden">
            <table className="table table-bordered text-center m-0">
                <thead className="table-dark small">
                    <tr>
                        {/* Dynamically label columns: W1, W2... and DUMMY if extra column exists */}
                        {result[0].map((_, j) => (
                            <th key={j} className="py-2">
                                {j < parseInt(customers) ? `W${j+1}` : "DUMMY"}
                            </th>
                        ))}
                    </tr>
                </thead>
                <tbody>
                {result.map((row, i) => (
                    <tr key={i}>
                    {row.map((val, j) => (
                        <td key={j} className={val > 0 ? "bg-success text-white fw-bold py-3" : "text-muted opacity-25 py-3"}>
                        {val > 0 ? val : '—'}
                        </td>
                    ))}
                    </tr>
                ))}
                </tbody>
            </table>
        </div>
        
        <div className="mt-4 p-3 bg-light rounded-3">
            <h6 className="fw-bold mb-3 border-bottom pb-2">Logistics Summary:</h6>
            <div style={{maxHeight: '200px', overflowY: 'auto'}}>
                {result.map((row, i) => row.map((val, j) => val > 0 && (
                    <div key={`${i}-${j}`} className="small py-2 border-bottom d-flex justify-content-between">
                        <span>
                            {i < parseInt(suppliers) ? `Factory ${i+1}` : <b className="text-danger">Dummy Source</b>} ➔ 
                            {j < parseInt(customers) ? ` Warehouse ${j+1}` : <b className="text-primary"> Dummy Storage</b>}
                        </span>
                        <b className="text-dark">{val} units</b>
                    </div>
                )))}
            </div>
        </div>
      </div>
      
      <div className="col-lg-5 text-center">
        <div className="p-5 bg-primary text-white rounded-4 shadow-lg h-100 d-flex flex-column justify-content-center">
            <Calculator size={50} className="mx-auto mb-3 opacity-50" />
            <h6 className="text-uppercase fw-bold opacity-75 tracking-wider">Total Shipping Cost</h6>
            <h1 className="display-2 fw-black">₹{totalCost}</h1>
            <div className="badge bg-white text-primary p-2 px-4 rounded-pill mt-3 shadow-sm mx-auto" style={{width: 'fit-content'}}>
                METHOD: {method}
            </div>
            
            <div className="mt-5 d-grid gap-2">
                <button onClick={() => setStep(2)} className="btn btn-outline-light rounded-pill fw-bold py-2 hover:bg-white hover:text-primary transition-all">
                    <Edit3 size={18} className="me-2"/> Edit Current Data
                </button>
                <button onClick={() => setStep(2)} className="btn btn-outline-light rounded-pill fw-bold py-2 hover:bg-white hover:text-primary transition-all">
                    <Settings size={18} className="me-2"/> Try Different Method
                </button>
                <button onClick={() => { setStep(1); setSuppliers(''); setCustomers(''); }} className="btn btn-light rounded-pill fw-bold py-3 text-primary shadow mt-2">
                    <RefreshCcw size={18} className="me-2"/> Start New Problem
                </button>
            </div>
        </div>
      </div>
    </div>
  </div>
)}

        {/* MODALS (Unbalanced & Vogel) */}
        {showUnbalancedModal && (
          <div className="modal show d-block" style={{backgroundColor: 'rgba(0,0,0,0.6)'}}>
            <div className="modal-dialog modal-dialog-centered">
              <div className="modal-content border-0 rounded-4 shadow-lg p-3">
                <div className="modal-body text-center">
                    <XCircle size={70} className="text-danger mb-3 mx-auto" />
                    <h3 className="fw-bold text-danger">UNBALANCED!</h3>
                    <p className="text-muted">Supply and Demand must match.</p>
                    <div className="bg-light p-3 rounded-3 mb-4 text-start small">
                        <div className="d-flex justify-content-between"><span>Total Supply:</span> <b>{unbalancedInfo.sTotal}</b></div>
                        <div className="d-flex justify-content-between"><span>Total Demand:</span> <b>{unbalancedInfo.dTotal}</b></div>
                        <hr/>
                        <div className="text-primary fw-bold">Adding a {unbalancedInfo.type} with {unbalancedInfo.diff} units at cost 0.</div>
                    </div>
                    <div className="d-flex gap-2">
                        <button className="btn btn-light w-50 fw-bold" onClick={()=>setShowUnbalancedModal(false)}>CANCEL</button>
                        <button className="btn btn-danger w-50 fw-bold" onClick={()=>runCalculation()}>SOLVE NOW</button>
                    </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {showVogelModal && (
          <div className="modal show d-block" style={{backgroundColor: 'rgba(0,0,0,0.8)'}}>
            <div className="modal-dialog modal-lg modal-dialog-centered">
              <div className="modal-content border-0 rounded-4 p-4 shadow-2xl">
                <div className="modal-header border-0">
                    <h4 className="fw-bold text-primary m-0">VAM PENALTY LOG</h4>
                    <button className="btn-close" onClick={()=>{setShowVogelModal(false); setStep(3);}}></button>
                </div>
                <div className="modal-body">
                    <div className="bg-light p-4 rounded-3 overflow-auto" style={{maxHeight: '350px'}}>
                        {vogelSteps.length > 0 ? vogelSteps.map((s, idx) => (
                            <div key={idx} className="mb-2 py-2 border-bottom font-monospace small">
                                <span className="text-primary fw-bold me-2">●</span> {s}
                            </div>
                        )) : <p className="text-muted">Calculating steps...</p>}
                    </div>
                    <button className="btn btn-primary w-100 mt-4 py-3 rounded-pill fw-bold shadow" onClick={()=>{setShowVogelModal(false); setStep(3);}}>
                        REVEAL FINAL ALLOCATION <CheckCircle size={20} className="ms-2" />
                    </button>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default App;