// 上海如静知华信息科技有限公司 https://www.zhuatech.cn/
import test from 'node:test';
import assert from 'node:assert/strict';
import {canAct,format,validField} from '../src/model.js';
test('viewer cannot mutate',()=>assert.equal(canAct({id:'v',role:'VIEWER'},{creator:'x',state:'DRAFT'},{from:['DRAFT'],role:'OPERATOR',separate:false}),false));
test('reviewer cannot self approve',()=>assert.equal(canAct({id:'r',role:'REVIEWER'},{creator:'r',state:'DRAFT'},{from:['DRAFT'],role:'REVIEWER',separate:true}),false));
test('reviewer can approve another person record',()=>assert.equal(canAct({id:'r',role:'REVIEWER'},{creator:'o',state:'DRAFT'},{from:['DRAFT'],role:'REVIEWER',separate:true}),true));
test('completed record hides action',()=>assert.equal(canAct({id:'a',role:'ADMIN'},{creator:'o',state:'CLOSED'},{from:['DRAFT'],role:'OPERATOR',separate:false}),false));
test('number boundary validation',()=>{assert.equal(validField({type:'integer',required:true,min:'1',max:'10'},2),true);assert.equal(validField({type:'integer',required:true,min:'1',max:'10'},1.5),false);assert.equal(validField({type:'money',required:true,min:'0.01'},0),false);assert.equal(validField({type:'money',required:true},'NaN'),false);});
test('required value and zero formatting',()=>{assert.equal(validField({type:'text',required:true},'  '),false);assert.equal(format(0),'0');assert.equal(format(null),'—');});
