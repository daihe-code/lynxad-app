package com.lynx.ad.modules.delivery.domain.service;

import com.lynx.ad.modules.delivery.domain.model.CreationState;
import java.util.*;

/** Changing a plan's product membership never discards another product's purchased generation work. */
public final class CreationProductSelection {
    private CreationProductSelection() {}
    public static boolean canAdjustWhileGenerating(CreationState s){
        return Set.of("RUNNING","IMPORTING").contains(s.generationStatus)&&s.engineJobId!=null
                &&s.pendingAdditionId==null&&s.pendingRegenerationId==null&&s.regeneration==null;
    }
    public static void apply(CreationState s,List<CreationState.Product> next) {
        var previous=new LinkedHashSet<>(s.products.stream().map(CreationState.Product::qcProductId).toList());
        var selected=new LinkedHashSet<>(next.stream().map(CreationState.Product::qcProductId).toList());
        if(previous.equals(selected))return;
        boolean generating=canAdjustWhileGenerating(s);
        if(generating&&s.generationGroupProducts.isEmpty())for(int n=0;n<s.products.size();n++)s.generationGroupProducts.put("group-"+(n+1),s.products.get(n).qcProductId());
        var added=next.stream().filter(p->!previous.contains(p.qcProductId())).toList();
        var removed=s.products.stream().filter(p->!selected.contains(p.qcProductId())).toList();
        int before=s.materials.size();
        for(var m:s.materials) {
            if(m.enginePlanId==null)m.enginePlanId=s.enginePlanId;
            if(!selected.contains(m.productId)&&!s.removedMaterialIds.contains(m.id))s.removedMaterialIds.add(m.id);
        }
        s.materials.removeIf(m->!selected.contains(m.productId));
        s.titlePools.keySet().retainAll(selected);
        s.generationPendingProductIds.removeIf(id->!selected.contains(id));
        for(var p:added)if(!s.generationPendingProductIds.contains(p.qcProductId()))s.generationPendingProductIds.add(p.qcProductId());
        s.generationSelection=s.generationSelection==null?new ArrayList<>():new ArrayList<>(s.generationSelection.stream().filter(selected::contains).toList());
        s.selectionChange=new CreationState.SelectionChange(added.stream().map(CreationState.Product::name).toList(),
                removed.stream().map(CreationState.Product::name).toList(),s.materials.size(),before-s.materials.size());
        s.groupingDecision=null;s.analysisStatus="NONE";s.analysis=null;s.analysisConfirmation=null;s.analysisRequestId=null;
        s.analysisError=null;s.analysisStartedAt=0;s.reachedStep=1;
        s.recommendations=null;
        if(!generating){s.enginePlanId=null;s.engineJobId=null;s.groups=null;
            s.generationStatus="NONE";s.generationMessage=null;s.generationProductIds.clear();s.generationGroupProducts.clear();
            s.engineTerminalStatus=null;s.engineTerminalJobId=null;s.resetGenerationReconciliation();
            s.progress=0;s.generationStage=null;s.generationCounts.clear();s.generationIssues.clear();
        }else{s.generationProductIds=new ArrayList<>(s.generationProductIds.stream().filter(selected::contains).toList());}
        s.generationRetry=null;s.regeneration=null;s.pendingAdditionId=null;s.pendingRegenerationId=null;
        if(s.publishedEdit==null){s.budget=null;s.roi2Goal=null;}
        s.step=1;s.mediaChanged();
    }
}
