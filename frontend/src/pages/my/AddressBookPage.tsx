import { useState, useEffect, type ChangeEvent, type FormEvent } from 'react';
import {
  getMyAddresses, addAddress, updateAddress, deleteAddress, setDefaultAddress,
} from '../../api/address';
import type { Address, AddressInput } from '../../types';

const EMPTY_FORM: AddressInput = { receiver: '', phone: '', address: '' };

const AddressBookPage = () => {
  const [addresses, setAddresses] = useState<Address[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [editingId, setEditingId] = useState<number | 'new' | null>(null); // null=닫힘, 'new'=추가, 숫자=수정
  const [form, setForm] = useState<AddressInput>(EMPTY_FORM);
  const [formError, setFormError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  const load = () => {
    setLoading(true);
    return getMyAddresses()
      .then((res) => setAddresses(res.data ?? []))
      .catch(() => setError('배송지 목록을 불러오지 못했습니다.'))
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, []);

  const openNew = () => { setEditingId('new'); setForm(EMPTY_FORM); setFormError(''); };
  const openEdit = (a: Address) => {
    setEditingId(a.id);
    setForm({ receiver: a.receiver, phone: a.phone, address: a.address });
    setFormError('');
  };
  const closeForm = () => { setEditingId(null); setForm(EMPTY_FORM); setFormError(''); };

  const handleChange = (e: ChangeEvent<HTMLInputElement>) => {
    setFormError('');
    setForm((f) => ({ ...f, [e.target.name]: e.target.value }));
  };

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    if (!form.receiver.trim() || !form.phone.trim() || !form.address.trim()) {
      setFormError('수령인, 연락처, 배송지를 모두 입력해주세요.');
      return;
    }
    setSubmitting(true);
    try {
      if (editingId === 'new') {
        await addAddress(form);
      } else {
        await updateAddress(editingId as number, form);
      }
      closeForm();
      await load();
    } catch {
      setFormError('저장에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async (id: number) => {
    if (!window.confirm('이 배송지를 삭제하시겠습니까?')) return;
    try {
      await deleteAddress(id);
      await load();
    } catch {
      setError('삭제에 실패했습니다.');
    }
  };

  const handleSetDefault = async (id: number) => {
    try {
      await setDefaultAddress(id);
      await load();
    } catch {
      setError('기본 배송지 지정에 실패했습니다.');
    }
  };

  if (loading) return (
    <div className="flex justify-center items-center h-[300px]">
      <div className="w-7 h-7 rounded-full animate-spin border-[3px] border-gray-200 border-t-brand-600" />
    </div>
  );

  return (
    <div className="max-w-[560px] mx-auto mt-6 sm:mt-10 px-4 flex flex-col gap-4 pb-10">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-bold text-gray-900 m-0">배송지 관리</h1>
        {editingId === null && (
          <button onClick={openNew} className="btn-brand-fill text-[13px]">배송지 추가</button>
        )}
      </div>

      {error && <div className="error-box">{error}</div>}

      {/* 추가/수정 폼 */}
      {editingId !== null && (
        <form onSubmit={handleSubmit} className="card flex flex-col gap-3">
          <div className="text-[15px] font-bold text-gray-900">
            {editingId === 'new' ? '새 배송지' : '배송지 수정'}
          </div>
          <div className="flex flex-col gap-1.5">
            <label className="field-label">수령인</label>
            <input name="receiver" value={form.receiver} onChange={handleChange}
              placeholder="수령인 이름" className="input-field pl-3" />
          </div>
          <div className="flex flex-col gap-1.5">
            <label className="field-label">연락처</label>
            <input name="phone" type="tel" value={form.phone} onChange={handleChange}
              placeholder="010-0000-0000" className="input-field pl-3" />
          </div>
          <div className="flex flex-col gap-1.5">
            <label className="field-label">배송지</label>
            <input name="address" value={form.address} onChange={handleChange}
              placeholder="도로명 주소를 입력하세요" className="input-field pl-3" />
          </div>
          {formError && <div className="error-box">{formError}</div>}
          <div className="flex justify-end gap-2 mt-1">
            <button type="button" onClick={closeForm} disabled={submitting}
              className="h-10 px-4 text-sm font-medium text-gray-600 bg-white border border-gray-200 rounded-[10px]">
              취소
            </button>
            <button type="submit" disabled={submitting}
              className="h-10 px-5 text-white text-sm font-semibold rounded-[10px] border-none disabled:opacity-70"
              style={{ background: 'linear-gradient(135deg, #4f46e5 0%, #6366f1 100%)' }}>
              {submitting ? '저장 중...' : '저장'}
            </button>
          </div>
        </form>
      )}

      {/* 목록 */}
      {addresses.length === 0 && editingId === null ? (
        <div className="flex flex-col items-center gap-3 py-20 text-center">
          <p className="text-gray-400 text-[15px] m-0">저장된 배송지가 없습니다.</p>
        </div>
      ) : (
        <div className="flex flex-col gap-3">
          {addresses.map((a) => (
            <div key={a.id} className="card flex flex-col gap-2">
              <div className="flex items-center gap-2">
                <span className="text-[15px] font-semibold text-gray-900">{a.receiver}</span>
                {a.isDefault && (
                  <span className="px-2 py-[2px] rounded-full text-[11px] font-semibold"
                    style={{ background: '#eef2ff', color: '#4f46e5' }}>
                    기본 배송지
                  </span>
                )}
              </div>
              <div className="text-[13px] text-gray-500">{a.phone}</div>
              <div className="text-[13px] text-gray-700">{a.address}</div>
              <div className="flex items-center gap-2 mt-1">
                {!a.isDefault && (
                  <button onClick={() => handleSetDefault(a.id)}
                    className="h-8 px-3 text-[12px] font-medium text-brand-600 border border-gray-200 rounded-lg hover:bg-gray-50 bg-white">
                    기본으로
                  </button>
                )}
                <button onClick={() => openEdit(a)}
                  className="h-8 px-3 text-[12px] font-medium text-gray-600 border border-gray-200 rounded-lg hover:bg-gray-50 bg-white">
                  수정
                </button>
                <button onClick={() => handleDelete(a.id)}
                  className="h-8 px-3 text-[12px] font-medium text-red-500 border border-red-200 rounded-lg hover:bg-red-50 bg-white">
                  삭제
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default AddressBookPage;
