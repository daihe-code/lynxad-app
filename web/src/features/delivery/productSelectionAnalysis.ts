export interface AnalysisFact { statement: string; evidence_ids: string[]; confirmation: 'observed' | 'merchant_confirmed' }
export interface CreativeDirection { title: string; display_content: string; fact_evidence_ids: string[]; required_assets: string[]; pending_claims: string[]; avoid: string[] }
export interface AnalysisReference { metric: string; value: number | null; unit: string | null; metric_definition: string; observed_period: string; evidence_ids: string[] }
export interface ProductSelectionAnalysis {
  schema_version: '1.0'; analysis_id: string; input_version: string; status: 'ready' | 'partial' | 'needs_input';
  summary: { title: string; message: string; next_action: string };
  coverage: { usable_dimensions: string[]; missing_dimensions: string[]; source_ids_used: string[] };
  combination: { status: 'retain' | 'review_grouping' | 'needs_input'; conclusion: string; evidence_ids: string[];
    hypotheses: { need_or_scene: string; basis: string; evidence_ids: string[]; validation_needed: string }[];
    proposed_groups: { group_id: string; product_ids: string[]; rationale: string; evidence_ids: string[]; requires_confirmation: true }[];
    unanswered_questions: string[] };
  product_analyses: { product_id: string; readiness: 'can_prepare' | 'needs_confirmation' | 'blocked'; known_facts: AnalysisFact[];
    issues: { code: string; severity: 'info' | 'needs_confirmation' | 'blocking'; message: string; affected_steps: string[]; evidence_ids: string[]; required_input: string }[];
    hypotheses: { need_or_scene: string; basis: string; evidence_ids: string[]; validation_needed: string }[];
    creative_directions: CreativeDirection[]; historical_reference: AnalysisReference[]; platform_reference: AnalysisReference[]; confirmation_questions: string[] }[];
  market_evidence: { dimension: string; product_ids: string[]; availability: 'available' | 'limited' | 'unavailable';
    observations: { statement: string; evidence_ids: string[] }[]; evidence_ids: string[];
    scope: Record<string, string | null>; limitation: string; missing_reason: string | null }[];
  handoff: { material: { product_id: string; approved_facts: AnalysisFact[]; candidate_directions: CreativeDirection[]; pending_confirmation: string[]; required_assets: string[]; blocked_claims: string[] }[];
    budget: { forecast_roi: null; recommended_daily_budget: null; required_inputs: string[]; historical_references: AnalysisReference[]; platform_references: AnalysisReference[]; grouping_considerations: string[]; limitations: string[] } };
  next_step: { can_enter_goal_setting: boolean; reasons: string[]; required_confirmations: string[]; blocked_operations: string[] };
  limitations: string[];
}
