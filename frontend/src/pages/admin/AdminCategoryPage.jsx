import { useState, useEffect } from 'react';
import {
  getCategories,
  createCategory,
  updateCategory,
  deleteCategory,
} from '../../api/product';

// B-05: 카테고리 관리 (ADMIN) — 목록 + 추가 + 인라인 수정 + 삭제
const AdminCategoryPage = () => {
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [newName, setNewName] = useState('');
  const [saving, setSaving] = useState(false);
  const [editId, setEditId] = useState(null);
  const [editName, setEditName] = useState('');

  const load = () =>
    getCategories()
      .then((res) => {
        setRows(res.data ?? []);
        setError('');
      })
      .catch(() => setError('카테고리 목록을 불러오지 못했습니다.'))
      .finally(() => setLoading(false));

  useEffect(() => {
    load();
  }, []);

  const handleCreate = async () => {
    const name = newName.trim();
    if (!name) {
      setError('카테고리명을 입력하세요.');
      return;
    }
    setSaving(true);
    try {
      await createCategory({ name });
      setNewName('');
      setError('');
      await load();
    } catch (err) {
      setError(err.response?.data?.detail || '카테고리 추가에 실패했습니다.');
    } finally {
      setSaving(false);
    }
  };

  const startEdit = (row) => {
    setEditId(row.id);
    setEditName(row.name);
    setError('');
  };

  const cancelEdit = () => {
    setEditId(null);
    setEditName('');
  };

  const handleUpdate = async (id) => {
    const name = editName.trim();
    if (!name) {
      setError('카테고리명을 입력하세요.');
      return;
    }
    try {
      await updateCategory(id, { name });
      cancelEdit();
      setError('');
      await load();
    } catch (err) {
      setError(err.response?.data?.detail || '카테고리 수정에 실패했습니다.');
    }
  };

  const handleDelete = async (row) => {
    if (!window.confirm(`'${row.name}' 카테고리를 삭제하시겠습니까?`)) return;
    try {
      await deleteCategory(row.id);
      setError('');
      await load();
    } catch (err) {
      setError(err.response?.data?.detail || '카테고리 삭제에 실패했습니다.');
    }
  };

  if (loading) {
    return (
      <div className="flex justify-center items-center py-32">
        <div className="w-7 h-7 rounded-full animate-spin border-[3px] border-gray-200 border-t-brand-600" />
      </div>
    );
  }

  return (
    <div className="max-w-[860px] mx-auto">
      <h1 className="text-xl font-bold text-gray-900 m-0 mb-6">카테고리 관리</h1>

      {error && <div className="error-box mb-4">{error}</div>}

      {/* 추가 폼 */}
      <div className="flex items-center gap-2 mb-6">
        <input
          value={newName}
          onChange={(e) => setNewName(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter') handleCreate(); }}
          maxLength={50}
          placeholder="새 카테고리명"
          className="flex-1 h-10 px-3 text-[14px] border border-gray-200 rounded-[10px] focus:outline-none focus:border-brand-600"
        />
        <button onClick={handleCreate} disabled={saving}
          className="h-10 px-5 text-[13px] font-medium text-white bg-brand-600 rounded-[10px] hover:bg-brand-700 transition-colors disabled:opacity-60">
          {saving ? '추가 중...' : '추가'}
        </button>
      </div>

      {rows.length === 0 ? (
        <div className="flex flex-col items-center gap-3 py-24 text-center">
          <p className="text-gray-400 text-[15px] m-0">등록된 카테고리가 없습니다.</p>
        </div>
      ) : (
        <div className="bg-white border border-gray-100 rounded-2xl overflow-hidden">
          <table className="w-full text-[13px]">
            <thead>
              <tr className="text-left text-gray-500 border-b border-gray-100">
                <th className="px-4 py-3 font-medium w-20">ID</th>
                <th className="px-4 py-3 font-medium">카테고리명</th>
                <th className="px-4 py-3 font-medium w-40 text-right">관리</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.id} className="border-b border-gray-50 last:border-0">
                  <td className="px-4 py-3 text-gray-400">#{row.id}</td>
                  <td className="px-4 py-3 text-gray-900 font-medium">
                    {editId === row.id ? (
                      <input
                        value={editName}
                        onChange={(e) => setEditName(e.target.value)}
                        onKeyDown={(e) => { if (e.key === 'Enter') handleUpdate(row.id); }}
                        maxLength={50}
                        autoFocus
                        className="w-full h-9 px-2.5 text-[13px] border border-gray-200 rounded-lg focus:outline-none focus:border-brand-600"
                      />
                    ) : (
                      row.name
                    )}
                  </td>
                  <td className="px-4 py-3 text-right whitespace-nowrap">
                    {editId === row.id ? (
                      <>
                        <button onClick={() => handleUpdate(row.id)}
                          className="text-[12px] font-medium text-brand-600 hover:underline bg-transparent border-none mr-3">
                          저장
                        </button>
                        <button onClick={cancelEdit}
                          className="text-[12px] font-medium text-gray-400 hover:underline bg-transparent border-none">
                          취소
                        </button>
                      </>
                    ) : (
                      <>
                        <button onClick={() => startEdit(row)}
                          className="text-[12px] font-medium text-gray-600 hover:underline bg-transparent border-none mr-3">
                          수정
                        </button>
                        <button onClick={() => handleDelete(row)}
                          className="text-[12px] font-medium text-red-500 hover:underline bg-transparent border-none">
                          삭제
                        </button>
                      </>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};

export default AdminCategoryPage;
